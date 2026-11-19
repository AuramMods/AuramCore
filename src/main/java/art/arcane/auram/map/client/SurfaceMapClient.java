package art.arcane.auram.map.client;

import art.arcane.auram.map.SurfaceMapNetwork;
import art.arcane.auram.map.SurfaceMapView;
import art.arcane.auram.map.SurfaceTile;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

public final class SurfaceMapClient implements SurfaceMapNetwork.ClientReceiver {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);
    private static final int FOREGROUND_REQUESTS = 20;
    private static final int BACKGROUND_REQUESTS = 4;
    private static final int MAX_PREFETCH_CANDIDATES = 24;
    private static final int MAX_IMPORT_READS = 2;
    private static final SurfaceMapClient INSTANCE = new SurfaceMapClient();
    private final ThreadPoolExecutor io = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), work -> {
                Thread worker = new Thread(work, "Auram surface tile cache");
                worker.setDaemon(true);
                return worker;
            });
    private List<RendererState> renderers = List.of();
    private volatile SurfaceMapNetwork.Hello server;
    // Accessed only on the client thread. Replaced when the active dimension changes.
    private DimensionSession dimension;

    private SurfaceMapClient() {}

    static void register(List<SurfaceMapRenderer> renderers) {
        INSTANCE.renderers = renderers.stream().map(RendererState::new).toList();
        SurfaceMapNetwork.setReceiver(INSTANCE);
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    public static boolean active() {
        return INSTANCE.server != null;
    }

    public static boolean covered(ResourceLocation dimension, int chunkX, int chunkZ) {
        return covered(SurfaceMapRenderer.Kind.JOURNEYMAP, dimension, chunkX, chunkZ);
    }

    static boolean covered(SurfaceMapRenderer.Kind kind, ResourceLocation dimension, int chunkX, int chunkZ) {
        if (active()) {
            var key = SurfaceTile.Key.atChunk(dimension, chunkX, chunkZ);
            for (var state : INSTANCE.renderers) {
                if (state.renderer.kind() == kind) {
                    var tile = state.imported.get(key);
                    return tile != null && tile.contains(SurfaceTile.slot(chunkX, chunkZ));
                }
            }
        }
        return false;
    }

    public static void ftbImageReleased(SurfaceTile.Key key) {
        Minecraft.getInstance().execute(() -> {
            for (var state : INSTANCE.renderers) {
                if (state.renderer.kind() == SurfaceMapRenderer.Kind.FTB_CHUNKS) {
                    state.imported.remove(key);
                }
            }
            var current = INSTANCE.dimension;
            if (current != null && current.id.equals(key.dimension())) {
                current.needsImport = true;
            }
        });
    }

    private void clearImports() {
        for (var state : renderers) {
            state.imported.clear();
            state.view = null;
        }
    }

    @Override
    public void hello(SurfaceMapNetwork.Hello hello) {
        clearImports();
        dimension = null;
        server = hello;
    }

    @SubscribeEvent
    public void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        server = null;
        dimension = null;
        clearImports();
    }

    @Override
    public void manifest(SurfaceMapNetwork.Manifest message) {
        DimensionSession current = dimension;
        if (current == null || !current.id.equals(message.dimension())) {
            return;
        }
        if (message.background()) {
            current.discoveryDue = System.nanoTime() + (message.more() ? SECOND / 5 : 5 * SECOND);
        }
        for (var stamp : message.tiles()) {
            var key = stamp.key(message.dimension());
            var version = current.cached.get(key);
            if (version == null || version.revision() != stamp.revision()) {
                current.candidates.merge(key, stamp.revision(), Math::max);
            }
        }
    }

    @Override
    public void tile(SurfaceMapNetwork.Tile message) {
        DimensionSession current = dimension;
        if (current == null || !current.id.equals(message.dimension())) {
            return;
        }
        var key = message.key();
        current.inFlight.remove(key);
        if (message.encoded().length == 0) {
            current.candidates.remove(key);
        } else {
            long revision = current.candidates.getOrDefault(key, 0L);
            current.candidates.remove(key);
            current.writing.add(key);
            try {
                io.execute(() -> persist(current, key, message.encoded()));
            } catch (RejectedExecutionException pressure) {
                current.writing.remove(key);
                current.candidates.put(key, revision);
            }
        }
    }

    private void persist(DimensionSession current, SurfaceTile.Key key, byte[] encoded) {
        try {
            SurfaceTile tile = current.cache.store(key, encoded);
            Minecraft.getInstance().execute(() -> {
                current.writing.remove(key);
                if (dimension == current) {
                    current.cached.put(key, tile.version());
                    current.candidates.computeIfPresent(key, (ignored, revision) ->
                            revision > tile.version().revision() ? revision : null);
                    if (!apply(key, tile)) {
                        current.needsImport = true;
                        current.importRetry = System.nanoTime() + SECOND / 4;
                    }
                }
            });
        } catch (IOException ex) {
            LOGGER.warn("Could not cache surface tile {}", key, ex);
            Minecraft.getInstance().execute(() -> current.writing.remove(key));
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (event.phase != TickEvent.Phase.END || player == null || server == null) {
            return;
        }
        var dimensionId = player.level().dimension().location();
        if (dimension == null || !dimension.id.equals(dimensionId)) {
            switchDimension(dimensionId);
        }
        DimensionSession current = dimension;
        SurfaceMapView view = null;
        for (var state : renderers) {
            var next = state.renderer.view(minecraft, player);
            if (next != null && (!next.dimension().equals(dimensionId) || !next.valid())) {
                next = null;
            }
            if (!Objects.equals(state.view, next)) {
                state.view = next;
                current.needsImport = true;
            }
            if (next != null) {
                view = view == null ? next : view.union(next);
            }
        }
        if (view == null) {
            int x = player.getBlockX();
            int z = player.getBlockZ();
            view = SurfaceMapRenderer.blocks(dimensionId, x, z, x, z, x, z);
        }
        if (!view.equals(current.view)) {
            SurfaceMapView previous = current.view;
            if (previous != null) {
                SurfaceMapView updated = view;
                current.inFlight.keySet().removeIf(key -> previous.contains(key) && !updated.contains(key));
            }
            current.view = view;
            SurfaceMapNetwork.sendView(view);
        }
        if (!current.indexLoaded) {
            return;
        }
        long now = System.nanoTime();
        warmVisible(current, now);
        requestTiles(current, now);
        if (current.candidates.size() < MAX_PREFETCH_CANDIDATES && now - current.discoveryDue >= 0) {
            SurfaceMapNetwork.discover();
            current.discoveryDue = now + 10 * SECOND;
        }
    }

    private void switchDimension(ResourceLocation id) {
        clearImports();
        Path root = Minecraft.getInstance().gameDirectory.toPath().resolve("auram-surface-cache")
                .resolve(server.world().toString());
        var session = new DimensionSession(id, new SurfaceTileCache(root));
        dimension = session;
        try {
            io.execute(() -> loadIndex(session));
        } catch (RejectedExecutionException pressure) {
            session.indexLoaded = true;
        }
    }

    private void loadIndex(DimensionSession session) {
        Map<SurfaceTile.Key, SurfaceTile.Version> versions;
        try {
            versions = session.cache.index(session.id);
        } catch (IOException ex) {
            LOGGER.warn("Could not index cached surface tiles for {}", session.id, ex);
            versions = Map.of();
        }
        var loaded = versions;
        Minecraft.getInstance().execute(() -> {
            if (dimension == session) {
                session.cached.putAll(loaded);
                session.candidates.entrySet().removeIf(entry -> {
                    var local = loaded.get(entry.getKey());
                    return local != null && local.revision() == entry.getValue();
                });
                session.indexLoaded = true;
            }
        });
    }

    private void warmVisible(DimensionSession current, long now) {
        if (!current.needsImport || now - current.importRetry < 0 || current.importReads >= MAX_IMPORT_READS) {
            return;
        }
        var center = current.view;
        var closest = current.cached.entrySet().stream()
                .filter(entry -> !current.reading.containsKey(entry.getKey()))
                .filter(entry -> renderers.stream().anyMatch(state -> state.needs(entry.getKey(), entry.getValue())))
                .min(Comparator.comparingLong(entry -> center.distanceSquared(entry.getKey())))
                .orElse(null);
        if (closest == null) {
            current.needsImport = current.importReads != 0;
            return;
        }
        var key = closest.getKey();
        current.reading.put(key, closest.getValue());
        current.importReads++;
        try {
            io.execute(() -> readVisible(current, key));
        } catch (RejectedExecutionException pressure) {
            current.reading.remove(key);
            current.importReads--;
        }
    }

    private void readVisible(DimensionSession current, SurfaceTile.Key key) {
        SurfaceTile tile;
        try {
            tile = current.cache.read(key);
        } catch (IOException ex) {
            tile = null;
        }
        SurfaceTile loaded = tile;
        Minecraft.getInstance().execute(() -> {
            current.reading.remove(key);
            current.importReads--;
            if (dimension == current) {
                if (loaded == null) {
                    current.cached.remove(key);
                } else if (!apply(key, loaded)) {
                    current.importRetry = System.nanoTime() + SECOND / 4;
                }
            }
        });
    }

    private boolean apply(SurfaceTile.Key key, SurfaceTile tile) {
        boolean applied = true;
        var version = tile.version();
        for (var state : renderers) {
            if (state.needs(key, version)) {
                if (state.renderer.apply(key, tile)) {
                    state.imported.put(key, version);
                } else {
                    applied = false;
                }
            }
        }
        return applied;
    }

    private static final class RendererState {
        private final SurfaceMapRenderer renderer;
        private final Map<SurfaceTile.Key, SurfaceTile.Version> imported = new ConcurrentHashMap<>();
        private SurfaceMapView view;

        RendererState(SurfaceMapRenderer renderer) {
            this.renderer = renderer;
        }

        boolean needs(SurfaceTile.Key key, SurfaceTile.Version version) {
            return view != null && view.contains(key) && !version.equals(imported.get(key));
        }
    }

    private void requestTiles(DimensionSession current, long now) {
        current.inFlight.entrySet().removeIf(entry -> now - entry.getValue() >= 0);
        if (current.inFlight.size() >= SurfaceMapNetwork.MAX_REQUESTS || current.candidates.isEmpty()) {
            return;
        }
        int nearby = (int) current.inFlight.keySet().stream().filter(current.view::contains).count();
        int background = current.inFlight.size() - nearby;
        if (nearby >= FOREGROUND_REQUESTS && background >= BACKGROUND_REQUESTS) {
            return;
        }
        var keys = current.candidates.keySet().stream()
                .filter(key -> !current.inFlight.containsKey(key) && !current.writing.contains(key))
                .sorted(Comparator.comparing((SurfaceTile.Key key) -> !current.view.contains(key))
                        .thenComparingLong(current.view::distanceSquared))
                .toList();
        var requests = new ArrayList<SurfaceMapNetwork.Stamp>();
        for (var key : keys) {
            if (current.inFlight.size() >= SurfaceMapNetwork.MAX_REQUESTS) {
                break;
            }
            boolean priority = current.view.contains(key);
            if (priority ? nearby >= FOREGROUND_REQUESTS : background >= BACKGROUND_REQUESTS) {
                continue;
            }
            var version = current.cached.get(key);
            requests.add(new SurfaceMapNetwork.Stamp(key.x(), key.z(), version == null ? 0L : version.revision()));
            double wait = priority ? 20.0 / server.nearbyRate() : 4.0 / server.backgroundRate();
            current.inFlight.put(key, now + (long) ((5 + wait) * SECOND));
            if (priority) {
                nearby++;
            } else {
                background++;
            }
        }
        if (!requests.isEmpty()) {
            SurfaceMapNetwork.fetch(new SurfaceMapNetwork.Fetch(current.id, requests));
        }
    }

    private static final class DimensionSession {
        private final ResourceLocation id;
        private final SurfaceTileCache cache;
        private final Map<SurfaceTile.Key, SurfaceTile.Version> cached = new HashMap<>();
        private final Map<SurfaceTile.Key, SurfaceTile.Version> reading = new HashMap<>();
        private final Map<SurfaceTile.Key, Long> candidates = new HashMap<>();
        private final Map<SurfaceTile.Key, Long> inFlight = new HashMap<>();
        private final Set<SurfaceTile.Key> writing = new HashSet<>();
        private SurfaceMapView view;
        private boolean needsImport = true;
        private long importRetry = System.nanoTime();
        private boolean indexLoaded;
        private long discoveryDue = System.nanoTime();
        private int importReads;

        DimensionSession(ResourceLocation id, SurfaceTileCache cache) {
            this.id = id;
            this.cache = cache;
        }
    }
}

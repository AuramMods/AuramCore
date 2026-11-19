package art.arcane.auram.map;

import art.arcane.auram.AuramConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Per-client request ordering and upload pacing. Index scans and tile reads stay off the tick thread. */
final class SurfaceMapStreaming implements AutoCloseable {
    private static final int BYTES_PER_SECOND = 1024 * 1024;
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);
    private static final long NEARBY_REFRESH = 2 * SECOND;
    private static final long DISCOVERY_REFRESH = 5 * SECOND;
    private static final int MAX_QUEUED = 2 * SurfaceMapNetwork.MAX_REQUESTS;
    private final SurfaceTileStore tiles;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final ThreadPoolExecutor io = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(24), work -> {
                Thread worker = new Thread(work, "Auram surface tile transfer");
                worker.setDaemon(true);
                return worker;
            });

    SurfaceMapStreaming(SurfaceTileStore tiles) {
        this.tiles = tiles;
    }

    void joined(ServerPlayer player) {
        if (SurfaceMapNetwork.available(player)) {
            sessions.put(player.getUUID(), new Session());
        }
    }

    void left(ServerPlayer player) {
        sessions.remove(player.getUUID());
    }

    void view(ServerPlayer player, SurfaceMapView view) {
        Session session = sessions.get(player.getUUID());
        if (session != null && session.hello && view.valid() && onDimension(player, view.dimension())) {
            if (!view.equals(session.view)) {
                if (session.view == null || !session.view.dimension().equals(view.dimension())) {
                    session.clearCoverage();
                } else {
                    SurfaceMapView previous = session.view;
                    session.queued.keySet().removeIf(key -> previous.contains(key) && !view.contains(key));
                }
                session.view = view;
                session.nearCatalog = null;
                session.nearDue = System.nanoTime();
            }
        }
    }

    void discover(ServerPlayer player) {
        Session session = sessions.get(player.getUUID());
        if (session != null && session.hello) {
            session.wantsDiscovery = true;
        }
    }

    void fetch(ServerPlayer player, SurfaceMapNetwork.Fetch request) {
        Session session = sessions.get(player.getUUID());
        if (session == null || session.view == null || !onDimension(player, request.dimension())) {
            return;
        }
        for (var stamp : request.tiles()) {
            if (SurfaceMapView.isWorldTile(stamp.x(), stamp.z())) {
                var key = stamp.key(request.dimension());
                if (session.queued.size() < MAX_QUEUED) {
                    session.queued.put(key, stamp.revision());
                }
            }
        }
    }

    void tick(MinecraftServer server) {
        long now = System.nanoTime();
        for (var player : server.getPlayerList().getPlayers()) {
            Session session = sessions.get(player.getUUID());
            if (session != null) {
                tick(player, session, now);
            }
        }
    }

    private void tick(ServerPlayer player, Session session, long now) {
        if (!session.hello) {
            if (tiles.worldId() != null) {
                SurfaceMapNetwork.send(player, new SurfaceMapNetwork.Hello(tiles.worldId(),
                        AuramConfig.SERVER.nearbyTilesPerSecond.get(), AuramConfig.SERVER.backgroundTilesPerSecond.get()));
                session.hello = true;
            }
            return;
        }
        SurfaceMapView view = session.view;
        if (view == null) {
            return;
        }
        if (!onDimension(player, view.dimension())) {
            session.clearCoverage();
            session.view = null;
            return;
        }
        sendNearby(player, session, view, now);
        sendDiscovery(player, session, view, now);
        sendTile(player, session, view, now);
    }

    private void sendNearby(ServerPlayer player, Session session, SurfaceMapView view, long now) {
        if (session.nearScan != null && session.nearScan.isDone()) {
            Nearby nearby = session.nearScan.getNow(null);
            session.nearScan = null;
            if (nearby != null && view.equals(nearby.view)) {
                session.nearCatalog = new Catalog(nearby.entries);
                session.nearDue = now + NEARBY_REFRESH;
            }
        }
        if (session.nearCatalog != null) {
            sendPage(player, view.dimension(), false, session.nearCatalog);
            if (!session.nearCatalog.hasMore()) {
                session.nearCatalog = null;
            }
        }
        if (session.nearScan == null && session.nearCatalog == null && now - session.nearDue >= 0) {
            session.nearScan = submit(() -> new Nearby(view, tiles.nearby(view)));
        }
    }

    private void sendDiscovery(ServerPlayer player, Session session, SurfaceMapView view, long now) {
        if (session.discoveryScan != null && session.discoveryScan.isDone()) {
            session.discovery = new Catalog(session.discoveryScan.getNow(List.of()));
            session.discoveryScan = null;
        }
        if (!session.wantsDiscovery) {
            return;
        }
        if (session.discovery != null) {
            sendPage(player, view.dimension(), true, session.discovery);
            session.wantsDiscovery = false;
            if (!session.discovery.hasMore()) {
                session.discovery = null;
                session.discoveryDue = now + DISCOVERY_REFRESH;
            }
        } else if (session.discoveryScan == null && now - session.discoveryDue >= 0) {
            var origin = SurfaceTile.Key.atChunk(view.dimension(), player.chunkPosition().x, player.chunkPosition().z);
            session.discoveryScan = submit(() -> tiles.available(origin.dimension(), origin.x(), origin.z()));
        }
    }

    private static void sendPage(ServerPlayer player, ResourceLocation dimension, boolean background, Catalog catalog) {
        List<SurfaceMapNetwork.Stamp> page = catalog.next();
        SurfaceMapNetwork.send(player, new SurfaceMapNetwork.Manifest(dimension, background, catalog.hasMore(), page));
    }

    private void sendTile(ServerPlayer player, Session session, SurfaceMapView view, long now) {
        int nearbyRate = AuramConfig.SERVER.nearbyTilesPerSecond.get();
        double backgroundRate = AuramConfig.SERVER.backgroundTilesPerSecond.get();
        if (session.read != null && session.read.isDone()) {
            var message = session.read.getNow(null);
            boolean nearby = view.contains(message.key());
            if (!session.queued.containsKey(message.key())) {
                session.read = null;
            } else if (message.encoded().length == 0 || session.ready(now, nearby, message.encoded().length)) {
                session.read = null;
                session.queued.remove(message.key());
                SurfaceMapNetwork.send(player, message);
                if (message.encoded().length != 0) {
                    session.uploaded(now, nearby, nearbyRate, backgroundRate, message.encoded().length);
                }
            } else {
                // A held background tile can lose priority when the viewport moves.
                SurfaceTile.Key next = nextTile(session, view, now);
                if (nearby || next == null || !view.contains(next)) {
                    return;
                }
                session.read = null;
            }
        }
        if (session.read != null || session.queued.isEmpty()) {
            return;
        }
        SurfaceTile.Key key = nextTile(session, view, now);
        if (key != null) {
            long known = session.queued.get(key);
            session.read = submit(() -> read(key, known));
        }
    }

    private SurfaceMapNetwork.Tile read(SurfaceTile.Key key, long known) {
        byte[] data = new byte[0];
        var version = tiles.version(key);
        if (version != null && version.revision() != known) {
            try {
                data = tiles.encodedTile(key);
            } catch (IOException ignored) {
                // The catalog will advertise the latest committed version again.
            }
        }
        return new SurfaceMapNetwork.Tile(key.dimension(), key.x(), key.z(), data);
    }

    private static SurfaceTile.Key nextTile(Session session, SurfaceMapView view, long now) {
        SurfaceTile.Key nearby = null;
        SurfaceTile.Key background = null;
        long nearbyDistance = Long.MAX_VALUE;
        long backgroundDistance = Long.MAX_VALUE;
        for (var key : session.queued.keySet()) {
            long distance = view.distanceSquared(key);
            if (view.contains(key)) {
                if (distance < nearbyDistance) {
                    nearby = key;
                    nearbyDistance = distance;
                }
            } else if (distance < backgroundDistance) {
                background = key;
                backgroundDistance = distance;
            }
        }
        // Keep the next nearby tile ready while waiting for its upload slot.
        return nearby != null ? nearby : session.backgroundRate.ready(now) ? background : null;
    }

    private static boolean onDimension(ServerPlayer player, ResourceLocation dimension) {
        return player.serverLevel().dimension().location().equals(dimension);
    }

    private <T> CompletableFuture<T> submit(Supplier<T> work) {
        try {
            return CompletableFuture.supplyAsync(work, io);
        } catch (RejectedExecutionException pressure) {
            return null;
        }
    }

    @Override
    public void close() {
        sessions.clear();
        io.shutdownNow();
    }

    private record Nearby(SurfaceMapView view, List<SurfaceTileStore.Entry> entries) {}

    private static final class Catalog {
        private final List<SurfaceTileStore.Entry> entries;
        private int cursor;

        Catalog(List<SurfaceTileStore.Entry> entries) {
            this.entries = entries;
        }

        boolean hasMore() {
            return cursor < entries.size();
        }

        List<SurfaceMapNetwork.Stamp> next() {
            int end = Math.min(entries.size(), cursor + SurfaceMapNetwork.MANIFEST_PAGE_SIZE);
            List<SurfaceMapNetwork.Stamp> page = entries.subList(cursor, end).stream()
                    .map(entry -> new SurfaceMapNetwork.Stamp(entry.key().x(), entry.key().z(), entry.version().revision()))
                    .toList();
            cursor = end;
            return page;
        }
    }

    private static final class Session {
        private final Map<SurfaceTile.Key, Long> queued = new LinkedHashMap<>();
        private final TileUploadRate totalRate = new TileUploadRate();
        private final TileUploadRate nearbyRate = new TileUploadRate();
        private final TileUploadRate backgroundRate = new TileUploadRate();
        private boolean hello;
        private SurfaceMapView view;
        private CompletableFuture<Nearby> nearScan;
        private Catalog nearCatalog;
        private long nearDue;
        private CompletableFuture<List<SurfaceTileStore.Entry>> discoveryScan;
        private Catalog discovery;
        private boolean wantsDiscovery;
        private long discoveryDue;
        private CompletableFuture<SurfaceMapNetwork.Tile> read;
        private long bytesStart;
        private int bytesSent;

        void clearCoverage() {
            queued.clear();
            nearScan = null;
            nearCatalog = null;
            nearDue = 0;
            discoveryScan = null;
            discovery = null;
            wantsDiscovery = false;
            discoveryDue = 0;
            read = null;
        }

        boolean ready(long now, boolean nearby, int bytes) {
            return totalRate.ready(now) && (nearby ? nearbyRate.ready(now) : backgroundRate.ready(now))
                    && (bytesSent == 0 || now - bytesStart >= SECOND || bytesSent + bytes <= BYTES_PER_SECOND);
        }

        void uploaded(long now, boolean nearby, int nearbyTilesPerSecond, double backgroundTilesPerSecond, int bytes) {
            totalRate.sent(now, Math.max(nearbyTilesPerSecond, backgroundTilesPerSecond));
            if (nearby) {
                nearbyRate.sent(now, nearbyTilesPerSecond);
            } else {
                backgroundRate.sent(now, backgroundTilesPerSecond);
            }
            if (bytesSent == 0 || now - bytesStart >= SECOND) {
                bytesStart = now;
                bytesSent = 0;
            }
            bytesSent += bytes;
        }
    }
}

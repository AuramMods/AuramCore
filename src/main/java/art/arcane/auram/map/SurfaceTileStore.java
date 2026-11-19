package art.arcane.auram.map;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

/** One worker owns tile assembly and writes. The chunk thread only copies and offers snapshots. */
final class SurfaceTileStore implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SNAPSHOT_CAPACITY = 128;
    private static final int MAX_DIRTY_TILES = 32;
    private static final long FLUSH_INTERVAL = TimeUnit.MILLISECONDS.toNanos(300);
    private final Path root;
    private final Semaphore snapshotSlots = new Semaphore(SNAPSHOT_CAPACITY);
    private final ConcurrentLinkedQueue<Work> snapshots = new ConcurrentLinkedQueue<>();
    private final Map<SurfaceTile.Key, SurfaceTile.Version> published = new ConcurrentHashMap<>();
    private final Map<SurfaceTile.Key, SurfaceTile> dirty = new LinkedHashMap<>();
    private final Map<SurfaceTile.Key, SurfaceTile> recent = new LinkedHashMap<>(32, 0.75f, true);
    private final Thread worker;
    private volatile boolean stopping;
    private final AtomicBoolean captureWarning = new AtomicBoolean();

    private record Work(SurfaceTile.Key key, int slot, SurfaceSnapshot snapshot, boolean refresh) {}

    SurfaceTileStore(Path root) {
        this.root = root;
        worker = new Thread(this::run, "Auram surface tiles");
        worker.start();
    }

    boolean capture(ServerLevel level, ChunkAccess chunk, boolean refresh) {
        if (stopping) {
            return false;
        }
        ChunkPos pos = chunk.getPos();
        SurfaceTile.Key key = SurfaceTile.Key.atChunk(level.dimension().location(), pos.x, pos.z);
        int slot = SurfaceTile.slot(pos.x, pos.z);
        SurfaceTile.Version version = published.get(key);
        if (!refresh && version != null && version.contains(slot)) {
            return true;
        }
        if (!snapshotSlots.tryAcquire()) {
            return false;
        }

        try {
            snapshots.add(new Work(key, slot, SurfaceSnapshot.capture(level, chunk), refresh));
            LockSupport.unpark(worker);
            return true;
        } catch (RuntimeException ex) {
            snapshotSlots.release();
            if (captureWarning.compareAndSet(false, true)) {
                LOGGER.error("Failed to capture terrain for the surface map at {} in {}", pos, key.dimension(), ex);
            }
            return false;
        }
    }

    private void run() {
        loadIndex();
        long flushAt = System.nanoTime() + FLUSH_INTERVAL;
        while (!stopping || !snapshots.isEmpty()) {
            Work work = snapshots.poll();
            if (work != null) {
                snapshotSlots.release();
                apply(work);
            }
            long now = System.nanoTime();
            if (dirty.size() >= MAX_DIRTY_TILES || now >= flushAt) {
                flush();
                flushAt = now + FLUSH_INTERVAL;
            }
            if (work == null && !stopping) {
                LockSupport.parkNanos(this, Math.max(1, flushAt - System.nanoTime()));
            }
        }
        flush();
    }

    private void apply(Work work) {
        SurfaceTile tile = dirty.get(work.key);
        if (tile == null) {
            tile = recent.get(work.key);
            if (tile == null) {
                try {
                    tile = read(work.key);
                } catch (IOException ex) {
                    LOGGER.warn("Could not read surface tile {}", work.key, ex);
                    tile = new SurfaceTile();
                }
            }
        }
        if ((work.refresh || !tile.version().contains(work.slot)) && tile.put(work.slot, work.snapshot.render())) {
            dirty.put(work.key, tile);
        }
        recent.put(work.key, tile);
        if (recent.size() > MAX_DIRTY_TILES) {
            recent.remove(recent.keySet().iterator().next());
        }
    }

    private void flush() {
        for (var entry : dirty.entrySet()) {
            SurfaceTile.Key key = entry.getKey();
            Path file = path(key);
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try {
                Files.createDirectories(file.getParent());
                try (var output = Files.newOutputStream(temp)) {
                    entry.getValue().write(output);
                }
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                published.put(key, entry.getValue().version());
            } catch (IOException ex) {
                LOGGER.warn("Could not persist surface tile {}", key, ex);
                recent.remove(key);
            }
        }
        dirty.clear();
    }

    private SurfaceTile read(SurfaceTile.Key key) throws IOException {
        Path file = path(key);
        if (!Files.isRegularFile(file)) {
            return new SurfaceTile();
        }
        try (var input = new BufferedInputStream(Files.newInputStream(file))) {
            return SurfaceTile.read(input);
        }
    }

    private Path path(SurfaceTile.Key key) {
        ResourceLocation dimension = key.dimension();
        String directory = dimension.getNamespace() + '~' + dimension.getPath().replace('/', '~');
        return root.resolve(directory).resolve(key.x() + "." + key.z() + ".amt");
    }

    private void loadIndex() {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> files = Files.find(root, 2, (path, attrs) -> attrs.isRegularFile() && path.toString().endsWith(".amt"))) {
            files.forEach(this::index);
        } catch (IOException | java.io.UncheckedIOException ex) {
            LOGGER.warn("Could not index surface tiles in {}", root, ex);
        }
    }

    private void index(Path file) {
        String directory = file.getParent().getFileName().toString();
        int split = directory.indexOf('~');
        String[] name = file.getFileName().toString().split("\\.");
        if (split < 1 || name.length != 3) {
            return;
        }
        try (var input = new BufferedInputStream(Files.newInputStream(file))) {
            var dimension = new ResourceLocation(directory.substring(0, split), directory.substring(split + 1).replace('~', '/'));
            var key = new SurfaceTile.Key(dimension, Integer.parseInt(name[0]), Integer.parseInt(name[1]));
            published.put(key, SurfaceTile.readVersion(input));
        } catch (RuntimeException | IOException ex) {
            LOGGER.warn("Could not index surface tile {}", file, ex);
        }
    }

    @Override
    public void close() {
        stopping = true;
        LockSupport.unpark(worker);
    }
}

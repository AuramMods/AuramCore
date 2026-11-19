package art.arcane.auram.smoke;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;

/** Measures locality inside actual structure-start passes without caching terrain. */
public final class LithoStartProbe {
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static final ThreadLocal<Frame> FRAME = new ThreadLocal<>();
    private static volatile Counts counts;

    public static void start() {
        if (Boolean.getBoolean("auram.smoke.traceLithoStarts")) counts = new Counts();
    }

    public static Frame enter(ChunkGenerator generator, RandomState random, ChunkAccess chunk) {
        Counts current = counts;
        if (current == null || generator.getClass() != NoiseBasedChunkGenerator.class) return null;
        Frame frame = new Frame(current, FRAME.get(), generator, random, chunk);
        FRAME.set(frame);
        return frame;
    }

    public static void leave(Frame frame) {
        if (frame == null) return;
        if (frame.previous == null) FRAME.remove();
        else FRAME.set(frame.previous);
        frame.counts.record(frame);
    }

    public static boolean projection(ChunkGenerator generator, int x, int z, Heightmap.Types type,
                                  LevelHeightAccessor height, RandomState random) {
        Counts current = counts;
        if (current == null) return false;
        Frame frame = FRAME.get();
        if (frame == null || frame.generator != generator || frame.height != height || frame.random != random) {
            current.outsideScope.increment();
            return false;
        }
        if (frame.heights == null) {
            frame.heights = new HashSet<>();
            frame.cells = new HashSet<>();
        }
        frame.calls++;
        frame.heights.add(new HeightKey(x, z, type));
        int width = ((NoiseBasedChunkGenerator) generator).generatorSettings().value().noiseSettings().getCellWidth();
        frame.cells.add(ChunkPos.asLong(Math.floorDiv(x, width), Math.floorDiv(z, width)));
        if (!frame.height.getPos().equals(new ChunkPos(x >> 4, z >> 4))) frame.outsideChunk++;
        return true;
    }

    public static long cpuTime() {
        return THREADS.getCurrentThreadCpuTime();
    }

    public static void sampled(long startCpu, long startedNanos, long setups) {
        Frame frame = FRAME.get();
        frame.cpuNanos += cpuTime() - startCpu;
        frame.wallNanos += System.nanoTime() - startedNanos;
        frame.setups += setups;
    }

    public static Map<String, Object> snapshot() {
        Counts current = counts;
        counts = null;
        return current == null ? Map.of() : current.snapshot();
    }

    private record HeightKey(int x, int z, Heightmap.Types type) {
    }

    public static final class Frame {
        private final Counts counts;
        private final Frame previous;
        private final ChunkGenerator generator;
        private final RandomState random;
        private final ChunkAccess height;
        private Set<HeightKey> heights;
        private Set<Long> cells;
        private int calls, outsideChunk;
        private long cpuNanos, wallNanos, setups;

        private Frame(Counts counts, Frame previous, ChunkGenerator generator, RandomState random, ChunkAccess height) {
            this.counts = counts;
            this.previous = previous;
            this.generator = generator;
            this.random = random;
            this.height = height;
        }
    }

    private static final class Counts {
        private final LongAdder outsideScope = new LongAdder();
        private final Map<Integer, Long> callsPerChunk = new TreeMap<>();
        private long calls, sameHeight, sameCell, outsideChunk, workerScopes;
        private long cpuNanos, wallNanos, setups;

        synchronized void record(Frame frame) {
            callsPerChunk.merge(frame.calls, 1L, Long::sum);
            if (frame.calls == 0) return;
            workerScopes++;
            calls += frame.calls;
            sameHeight += frame.calls - frame.heights.size();
            sameCell += frame.calls - frame.cells.size();
            outsideChunk += frame.outsideChunk;
            cpuNanos += frame.cpuNanos;
            wallNanos += frame.wallNanos;
            setups += frame.setups;
        }

        synchronized Map<String, Object> snapshot() {
            return Map.of("calls_per_create_structures", new TreeMap<>(callsPerChunk), "active_scopes", workerScopes,
                    "projections", calls, "repeated_heights", sameHeight, "repeated_terrain_cells", sameCell,
                    "outside_origin_chunk", outsideChunk, "outside_matching_scope", outsideScope.sum(),
                    "projection_cpu_nanos", cpuNanos, "projection_wall_nanos", wallNanos, "noise_chunk_setups", setups);
        }
    }
}

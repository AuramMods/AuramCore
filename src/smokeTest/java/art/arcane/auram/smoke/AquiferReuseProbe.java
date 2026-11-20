package art.arcane.auram.smoke;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;

/** Observes completed vanilla scans without substituting their results. */
public final class AquiferReuseProbe {
    public static final boolean VERIFY_HEIGHTS = Boolean.getBoolean("auram.smoke.verifyAquiferHeights");
    public static final boolean ENABLED = VERIFY_HEIGHTS || Boolean.getBoolean("auram.smoke.aquiferReuse");
    public static final boolean TIME_STAGES = ENABLED || Boolean.getBoolean("auram.smoke.stageTimings");
    private static final int SLOTS = 1 << 17;
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private static final Run STARTUP = new Run();
    private static final Run COLD = new Run();
    private static volatile Run active = STARTUP;

    private AquiferReuseProbe() {}

    public enum Stage { STARTS, NOISE, CARVERS, BIOMES, SURFACE, FEATURES }

    public static Scope enter(Stage stage, ChunkGenerator generator, RandomState random) {
        Scope previous = CURRENT.get();
        Scope scope = new Scope(active, stage, generator, random, previous, THREADS.getCurrentThreadCpuTime());
        CURRENT.set(scope);
        return scope;
    }

    public static void leave(Scope scope) {
        scope.run.stats.get(scope.stage).stageNanos.add(THREADS.getCurrentThreadCpuTime() - scope.started);
        scope.run.stats.get(scope.stage).tasks.increment();
        if (scope.previous == null) CURRENT.remove();
        else CURRENT.set(scope.previous);
    }

    public record Scope(Run run, Stage stage, ChunkGenerator generator, RandomState random,
                        Scope previous, long started) {}

    private record Key(ChunkGenerator generator, RandomState random, NoiseSettings settings, long column) {}

    private record Observation(Key key, int value, long thread) {}

    private static final class Run {
        final AtomicReferenceArray<Observation> slots = ENABLED ? new AtomicReferenceArray<>(SLOTS) : null;
        final EnumMap<Stage, Stats> stats = new EnumMap<>(Stage.class);

        Run() {
            for (Stage stage : Stage.values()) stats.put(stage, new Stats());
        }
    }

    private static final class Stats {
        final LongAdder tasks = new LongAdder();
        final LongAdder stageNanos = new LongAdder();
        final LongAdder localHits = new LongAdder();
        final LongAdder scans = new LongAdder();
        final LongAdder excluded = new LongAdder();
        final LongAdder sameWorker = new LongAdder();
        final LongAdder differentWorker = new LongAdder();
        final LongAdder mismatches = new LongAdder();
        final LongAdder sharedLookups = new LongAdder();
        final LongAdder sharedHits = new LongAdder();
        final LongAdder verifiedHeights = new LongAdder();
        final LongAdder cpuSamples = new LongAdder();
        final LongAdder scanNanos = new LongAdder();
    }

    public static Scope current() {
        return CURRENT.get();
    }

    public static long beforeScan(Scope scope, boolean localHit) {
        Stats stats = scope.run.stats.get(scope.stage);
        if (localHit) {
            stats.localHits.increment();
            return -1;
        }
        return THREADS.getCurrentThreadCpuTime();
    }

    public static void afterScan(Scope scope, long started, boolean unblended, NoiseSettings settings,
                                 long column, int result) {
        if (started < 0) return;
        Stats stats = scope.run.stats.get(scope.stage);
        stats.scans.increment();
        long elapsed = THREADS.getCurrentThreadCpuTime() - started;
        if (elapsed >= 0) {
            stats.scanNanos.add(elapsed);
            stats.cpuSamples.increment();
        }
        if (!unblended || scope.random == null) {
            stats.excluded.increment();
            return;
        }
        Key key = new Key(scope.generator, scope.random, settings, column);
        // Mix the packed coordinates before narrowing to int. X xor Z
        // collapses adjacent diagonal columns into the same bucket.
        long hash = HashCommon.mix(column);
        int domain = 31 * (31 * System.identityHashCode(scope.generator)
                + System.identityHashCode(scope.random)) + settings.hashCode();
        int index = ((int) hash ^ domain) & (SLOTS - 1);
        long thread = Thread.currentThread().getId();
        Observation previous = scope.run.slots.getAndSet(index, new Observation(key, result, thread));
        if (previous != null && previous.key.equals(key)) {
            if (previous.value != result) stats.mismatches.increment();
            if (previous.thread == thread) stats.sameWorker.increment();
            else stats.differentWorker.increment();
        }
    }

    public static void sharedLookup(boolean hit) {
        Scope scope = CURRENT.get();
        if (scope == null) return;
        Stats stats = scope.run.stats.get(scope.stage);
        stats.sharedLookups.increment();
        if (hit) stats.sharedHits.increment();
    }

    static long sharedLookups(Scope scope) {
        return scope.run.stats.get(scope.stage).sharedLookups.sum();
    }

    public static void verifiedHeight(Scope scope) {
        scope.run.stats.get(scope.stage).verifiedHeights.increment();
    }

    public static boolean fillingBiomes() {
        Scope scope = CURRENT.get();
        return scope != null && scope.stage() == Stage.BIOMES;
    }

    public static void beginColdRun() {
        active = COLD;
    }

    public static void write(Path path) throws IOException {
        if (!TIME_STAGES) return;
        StringBuilder csv = new StringBuilder("phase,stage,tasks,stage_cpu_ns,local_hits,scans,excluded,"
                + "same_worker_repeats,cross_worker_repeats,mismatches,timed_scans,scan_cpu_ns,verified_heights,shared_lookups,shared_hits\n");
        append(csv, "startup", STARTUP);
        append(csv, "cold", COLD);
        Files.writeString(path, csv);
    }

    private static void append(StringBuilder csv, String phase, Run run) {
        run.stats.forEach((stage, stats) -> csv.append(phase).append(',').append(stage).append(',')
                .append(stats.tasks.sum()).append(',').append(stats.stageNanos.sum()).append(',')
                .append(stats.localHits.sum()).append(',').append(stats.scans.sum()).append(',')
                .append(stats.excluded.sum()).append(',').append(stats.sameWorker.sum()).append(',')
                .append(stats.differentWorker.sum()).append(',').append(stats.mismatches.sum()).append(',')
                .append(stats.cpuSamples.sum()).append(',').append(stats.scanNanos.sum()).append(',')
                .append(stats.verifiedHeights.sum()).append(',').append(stats.sharedLookups.sum()).append(',')
                .append(stats.sharedHits.sum()).append('\n'));
    }
}

package art.arcane.auram.smoke;

import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.levelgen.RandomState;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class SurfaceWorkProbe {
    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static volatile Counter active;

    public static void start(RandomState state) {
        active = new Counter(state);
    }

    public static Sample enter(RandomState state, BiomeManager manager) {
        Counter counter = active;
        if (counter == null || counter.state != state) return null;
        long thread = Thread.currentThread().getId();
        counter.workers.add(thread);
        counter.managerTypes.add(manager.getClass().getName());
        return new Sample(counter, thread, THREADS.getThreadAllocatedBytes(thread), THREADS.getCurrentThreadCpuTime());
    }

    public static void leave(Sample sample) {
        if (sample == null) return;
        var counter = sample.counter();
        counter.cpu.add(THREADS.getCurrentThreadCpuTime() - sample.cpu());
        counter.bytes.add(THREADS.getThreadAllocatedBytes(sample.thread()) - sample.allocated());
        counter.calls.increment();
    }

    public static void observeGetter(RandomState state, Object getter) {
        Counter counter = active;
        if (counter == null || counter.state != state) return;
        String type = getter.getClass().getName();
        int lambda = type.indexOf("$$Lambda");
        if (lambda >= 0) type = type.substring(0, lambda);
        counter.getterTypes.computeIfAbsent(type, ignored -> new LongAdder()).increment();
    }

    public static Result stop() {
        Counter counter = active;
        active = null;
        Map<String, Long> getters = new TreeMap<>();
        counter.getterTypes.forEach((type, count) -> getters.put(type, count.sum()));
        return new Result(counter.calls.sum(), counter.cpu.sum(), counter.bytes.sum(), counter.workers.size(),
                Set.copyOf(counter.managerTypes), getters);
    }

    public record Sample(Counter counter, long thread, long allocated, long cpu) {
    }

    public record Result(long builds, long cpuNanos, long allocatedBytes, int workers, Set<String> managerTypes,
                         Map<String, Long> getterTypes) {
    }

    private static final class Counter {
        private final RandomState state;
        private final Set<Long> workers = ConcurrentHashMap.newKeySet();
        private final Set<String> managerTypes = ConcurrentHashMap.newKeySet();
        private final Map<String, LongAdder> getterTypes = new ConcurrentHashMap<>();
        private final LongAdder calls = new LongAdder(), cpu = new LongAdder(), bytes = new LongAdder();

        private Counter(RandomState state) {
            this.state = state;
        }
    }
}

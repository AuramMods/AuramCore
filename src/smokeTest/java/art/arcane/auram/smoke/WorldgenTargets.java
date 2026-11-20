package art.arcane.auram.smoke;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.concurrent.atomic.LongAdder;

public final class WorldgenTargets {
    public static final boolean ENABLED = Boolean.getBoolean("auram.smoke.targetWorldgen");
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static final EnumMap<Target, Totals> STARTUP = totals();
    private static final EnumMap<Target, Totals> COLD = totals();
    private static volatile EnumMap<Target, Totals> phase = STARTUP;

    public enum Target { TERRA_READ_LOCK, TERRA_WRITE_LOCK }

    private static final class Totals {
        final LongAdder calls = new LongAdder();
        final LongAdder cpu = new LongAdder();
        final LongAdder wall = new LongAdder();
        final LongAdder offCpu = new LongAdder();
    }

    private WorldgenTargets() {}

    public static long cpu() {
        return THREADS.getCurrentThreadCpuTime();
    }

    public static void record(Target target, long cpuNanos, long wallNanos) {
        var data = phase.get(target);
        data.calls.increment();
        data.cpu.add(cpuNanos);
        data.wall.add(wallNanos);
        if (wallNanos != 0) data.offCpu.add(Math.max(0, wallNanos - cpuNanos));
    }

    public static void beginCold() {
        phase = COLD;
    }

    public static void write() throws IOException {
        if (!ENABLED) return;
        StringBuilder csv = new StringBuilder("phase,target,calls,cpu_ns,wall_ns,off_cpu_ns\n");
        append(csv, "startup", STARTUP);
        append(csv, "cold", COLD);
        Files.writeString(Path.of("worldgen-targets.csv"), csv);
    }

    private static EnumMap<Target, Totals> totals() {
        var totals = new EnumMap<Target, Totals>(Target.class);
        for (var target : Target.values()) totals.put(target, new Totals());
        return totals;
    }

    private static void append(StringBuilder csv, String name, EnumMap<Target, Totals> totals) {
        totals.forEach((target, data) -> csv.append(name).append(',').append(target).append(',')
                .append(data.calls.sum()).append(',').append(data.cpu.sum()).append(',')
                .append(data.wall.sum()).append(',').append(data.offCpu.sum()).append('\n'));
    }
}

package art.arcane.auram.smoke;

import net.minecraft.Util;

import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

final class SchedulingSmoke {
    private SchedulingSmoke() {
    }

    static Map<String, Object> capture() throws Exception {
        var main = Util.backgroundExecutor();
        var io = Util.ioPool();
        boolean stable = main == Util.backgroundExecutor() && io == Util.ioPool();
        return Map.of(
                "calling_thread", thread(),
                "main", main.submit(SchedulingSmoke::thread).get(15, TimeUnit.SECONDS),
                "io", io.submit(SchedulingSmoke::thread).get(15, TimeUnit.SECONDS),
                "stable", stable,
                "parallelism", main instanceof ForkJoinPool pool ? pool.getParallelism() : -1,
                "async", main instanceof ForkJoinPool pool && pool.getAsyncMode());
    }

    private static Map<String, Object> thread() {
        Thread thread = Thread.currentThread();
        return Map.of("name", thread.getName(), "priority", thread.getPriority(), "daemon", thread.isDaemon());
    }
}

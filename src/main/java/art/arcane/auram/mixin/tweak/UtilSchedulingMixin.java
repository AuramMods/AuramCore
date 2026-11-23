package art.arcane.auram.mixin.tweak;

import art.arcane.auram.tweak.ThreadScheduling;
import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadFactory;

@Mixin(Util.class)
public abstract class UtilSchedulingMixin {
    @ModifyArg(method = "makeExecutor", index = 0, at = @At(value = "INVOKE", remap = false,
            target = "Ljava/util/concurrent/ForkJoinPool;<init>(ILjava/util/concurrent/ForkJoinPool$ForkJoinWorkerThreadFactory;Ljava/lang/Thread$UncaughtExceptionHandler;Z)V"))
    private static int auram$configureMainWorkerCount(int original) {
        ThreadScheduling settings = ThreadScheduling.SETTINGS;
        return settings.enabled() ? settings.mainWorkers(original) : original;
    }

    @ModifyArg(method = "makeExecutor", index = 1, at = @At(value = "INVOKE", remap = false,
            target = "Ljava/util/concurrent/ForkJoinPool;<init>(ILjava/util/concurrent/ForkJoinPool$ForkJoinWorkerThreadFactory;Ljava/lang/Thread$UncaughtExceptionHandler;Z)V"))
    private static ForkJoinPool.ForkJoinWorkerThreadFactory auram$configureMainWorkerFactory(
            ForkJoinPool.ForkJoinWorkerThreadFactory original) {
        ThreadScheduling settings = ThreadScheduling.SETTINGS;
        if (!settings.enabled()) return original;
        return pool -> {
            var thread = original.newThread(pool);
            settings.configureMainWorker(thread);
            return thread;
        };
    }

    @ModifyArg(method = "makeIoExecutor", at = @At(value = "INVOKE", remap = false,
            target = "Ljava/util/concurrent/Executors;newCachedThreadPool(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;"))
    private static ThreadFactory auram$configureIoPool(ThreadFactory original) {
        ThreadScheduling settings = ThreadScheduling.SETTINGS;
        if (!settings.enabled()) return original;
        return task -> {
            Thread thread = original.newThread(task);
            settings.configureIoWorker(thread);
            return thread;
        };
    }
}

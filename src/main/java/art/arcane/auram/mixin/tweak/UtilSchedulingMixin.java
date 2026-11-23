package art.arcane.auram.mixin.tweak;

import art.arcane.auram.tweak.ThreadScheduling;
import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadFactory;

@Mixin(Util.class)
public abstract class UtilSchedulingMixin {
    @ModifyArgs(method = "makeExecutor", at = @At(value = "INVOKE", remap = false,
            target = "Ljava/util/concurrent/ForkJoinPool;<init>(ILjava/util/concurrent/ForkJoinPool$ForkJoinWorkerThreadFactory;Ljava/lang/Thread$UncaughtExceptionHandler;Z)V"))
    private static void auram$configureMainPool(Args args) {
        ThreadScheduling settings = ThreadScheduling.SETTINGS;
        if (!settings.enabled()) return;

        ForkJoinPool.ForkJoinWorkerThreadFactory original = args.get(1);
        args.set(0, settings.mainWorkers(args.get(0)));
        args.set(1, (ForkJoinPool.ForkJoinWorkerThreadFactory) pool -> {
            var thread = original.newThread(pool);
            settings.configureMainWorker(thread);
            return thread;
        });
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

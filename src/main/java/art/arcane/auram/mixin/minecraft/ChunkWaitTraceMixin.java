package art.arcane.auram.mixin.minecraft;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

@Mixin(ServerChunkCache.class)
public abstract class ChunkWaitTraceMixin {
    @Unique private static final Logger WAIT_LOG = LogUtils.getLogger();
    @Unique private static final StackWalker WALKER = StackWalker.getInstance();
    @Unique private static final long MIN_WAIT = TimeUnit.MILLISECONDS.toNanos(10);
    @Unique private static final long LOG_INTERVAL = TimeUnit.SECONDS.toNanos(5);
    @Unique private static final AtomicLong LAST_LOG = new AtomicLong(System.nanoTime() - LOG_INTERVAL);
    @Unique private static final AtomicLong SUPPRESSED = new AtomicLong();

    @Shadow @Final private ServerLevel level;

    @WrapOperation(method = {"getChunk", "getChunkFuture"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerChunkCache$MainThreadExecutor;managedBlock(Ljava/util/function/BooleanSupplier;)V"))
    private void auram$tracePendingChunk(@Coerce Object executor, BooleanSupplier ready, Operation<Void> original,
                                          int x, int z, ChunkStatus status, boolean create) {
        if (ready.getAsBoolean()) {
            original.call(executor, ready);
            return;
        }

        long started = System.nanoTime();
        try {
            original.call(executor, ready);
        } finally {
            long finished = System.nanoTime();
            if (finished - started >= MIN_WAIT) auram$logChunkWait(started, finished, x, z, status, create);
        }
    }

    @Unique
    private void auram$logChunkWait(long started, long finished, int x, int z, ChunkStatus status, boolean create) {
        long previous = LAST_LOG.get();
        if (finished - previous < LOG_INTERVAL || !LAST_LOG.compareAndSet(previous, finished)) {
            SUPPRESSED.incrementAndGet();
            return;
        }
        String caller = WALKER.walk(frames -> frames
                .dropWhile(frame -> frame.getClassName().equals(ServerChunkCache.class.getName())
                        || frame.getClassName().startsWith("com.llamalad7.mixinextras."))
                .limit(14)
                .map(frame -> frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber())
                .collect(Collectors.joining(" <- ")));
        WAIT_LOG.warn("Auram chunk wait {} ms dimension={} chunk=[{}, {}] status={} create={} suppressed={} caller={}",
                TimeUnit.NANOSECONDS.toMillis(finished - started), level.dimension().location(), x, z, status, create,
                SUPPRESSED.getAndSet(0), caller);
    }
}

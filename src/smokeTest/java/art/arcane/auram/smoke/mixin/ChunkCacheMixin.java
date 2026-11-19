package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.ChunkCacheProbe;
import art.arcane.auram.smoke.FtbRtpSmoke;
import art.arcane.auram.smoke.MovementWaitProbe;
import art.arcane.auram.smoke.PacketMovementSmoke;
import art.arcane.auram.smoke.PatchSmokeMod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

@Mixin(ServerChunkCache.class)
public abstract class ChunkCacheMixin implements ChunkCacheProbe {
    @Invoker("runDistanceManagerUpdates")
    public abstract boolean auramSmoke$runDistanceManagerUpdates();

    @Inject(method = "getChunkFutureMainThread", at = @At("RETURN"), cancellable = true)
    private void auramSmoke$delayRtpBiome(int x, int z, ChunkStatus status, boolean create,
                        CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> ci) {
        if (status != ChunkStatus.BIOMES || create) return;
        var future = ci.getReturnValue();
        var held = FtbRtpSmoke.holdFirstCandidateBiome((ServerChunkCache) (Object) this, x, z, future);
        if (held != future) ci.setReturnValue(held);
    }

    @WrapOperation(method = "getChunk", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerChunkCache$MainThreadExecutor;managedBlock(Ljava/util/function/BooleanSupplier;)V"))
    private void auramSmoke$timeMovementWait(@Coerce Object executor, BooleanSupplier ready, Operation<Void> original,
                                           int x, int z, ChunkStatus status, boolean create) {
        if (!MovementWaitProbe.observing() || status != ChunkStatus.FULL || ready.getAsBoolean()) {
            original.call(executor, ready);
            return;
        }
        long began = System.nanoTime();
        try {
            original.call(executor, ready);
        } finally {
            MovementWaitProbe.chunkWait(System.nanoTime() - began, create);
        }
    }

    @Inject(method = "getChunk", at = @At("HEAD"))
    private void auramSmoke$rejectBlockingLookup(int x, int z, ChunkStatus status, boolean create,
                                                CallbackInfoReturnable<ChunkAccess> ci) {
        PacketMovementSmoke.observeChunkLookup(x, z, status, create);
        if (PatchSmokeMod.forbiddenChunkLookups.contains(ChunkPos.asLong(x, z))) {
            throw new AssertionError("Patched hook requested a blocking chunk lookup at " + x + "," + z);
        }
    }
}

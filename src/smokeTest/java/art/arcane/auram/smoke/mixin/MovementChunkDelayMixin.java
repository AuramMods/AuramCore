package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.MovementWaitProbe;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkMap.class)
public abstract class MovementChunkDelayMixin {
    @Shadow @Final ServerLevel level;

    @Inject(method = "schedule", at = @At("RETURN"), cancellable = true)
    private void auramSmoke$delayOneFullChunk(ChunkHolder holder, ChunkStatus status,
            CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> ci) {
        if (status == ChunkStatus.FULL && level.dimension() == Level.OVERWORLD
                && MovementWaitProbe.delayChunk(holder.getPos())) {
            ci.setReturnValue(MovementWaitProbe.holdResult(ci.getReturnValue()));
        }
    }
}

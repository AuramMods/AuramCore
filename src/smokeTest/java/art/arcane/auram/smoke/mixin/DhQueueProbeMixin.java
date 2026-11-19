package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.DistantHorizonsSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.generation.WorldGenerationQueue", remap = false)
public abstract class DhQueueProbeMixin implements DistantHorizonsSmoke.Queue {
    @Inject(method = "startAndSetTargetPos", at = @At("HEAD"))
    private void auram$observeQueue(CallbackInfo ci) {
        if (DistantHorizonsSmoke.SERVER) DistantHorizonsSmoke.observeQueue(this);
    }

    @Inject(method = "isGeneratorBusy", at = @At("RETURN"), cancellable = true)
    private void auram$experimentWithForegroundPriority(CallbackInfoReturnable<Boolean> ci) {
        if (!ci.getReturnValueZ() && DistantHorizonsSmoke.deferBackgroundBatch()) ci.setReturnValue(true);
    }
}

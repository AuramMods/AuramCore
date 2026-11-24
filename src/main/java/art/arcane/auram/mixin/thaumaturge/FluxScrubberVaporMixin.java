package art.arcane.auram.mixin.thaumaturge;

import art.arcane.auram.vapor.ThaumaturgeVapor;
import com.leclowndu93150.thaumaturge.content.device.fluxscrubber.BlockEntityFluxScrubber;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(value = BlockEntityFluxScrubber.class, remap = false)
abstract class FluxScrubberVaporMixin {
    @Inject(method = "scrubAt", at = @At("RETURN"))
    private void auram$vapor(CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) ThaumaturgeVapor.machine((BlockEntityFluxScrubber) (Object) this, 10);
    }
}

package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.AquiferReuseProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "art.arcane.auram.util.PreliminarySurfaceCache", remap = false)
public abstract class AquiferSharedHitProbeMixin {
    @Inject(method = "lookup", at = @At("RETURN"))
    private void auramSmoke$recordSharedLookup(long column, CallbackInfoReturnable<Object> cir) {
        if (AquiferReuseProbe.TIME_STAGES) {
            AquiferReuseProbe.sharedLookup(cir.getReturnValue() != null);
        }
    }
}

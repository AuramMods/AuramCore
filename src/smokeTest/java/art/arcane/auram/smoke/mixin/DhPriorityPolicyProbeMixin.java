package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.DistantHorizonsSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "art.arcane.auram.util.DistantGenerationPriority", remap = false)
public abstract class DhPriorityPolicyProbeMixin {
    @Inject(method = "shouldDefer", at = @At("RETURN"))
    private static void auram$recordDecision(boolean pending, int running, CallbackInfoReturnable<Boolean> ci) {
        if (DistantHorizonsSmoke.SERVER && ci.getReturnValueZ()) {
            DistantHorizonsSmoke.recordDeferral();
        }
    }
}

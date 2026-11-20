package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.WorldgenTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.dreamtinker.dreamtinker.library.worldgen.TagAndTagRuleTest", remap = false)
public abstract class DreamtinkerTagProbeMixin {
    @Inject(method = {"m_213865_", "test"}, at = @At("HEAD"))
    private void auramSmoke$countTagChecks(CallbackInfoReturnable<Boolean> cir) {
        if (WorldgenTargets.ENABLED) WorldgenTargets.record(WorldgenTargets.Target.DREAM_TAG_TEST, 0, 0);
    }
}

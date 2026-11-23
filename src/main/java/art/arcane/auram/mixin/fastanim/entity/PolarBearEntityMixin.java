// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim.entity;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.world.entity.animal.PolarBear;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PolarBear.class)
public class PolarBearEntityMixin {
    @Shadow
    private float clientSideStandAnimationO;
    @Shadow
    private float clientSideStandAnimation;

    @Inject(at = @At("HEAD"), method = "getStandingAnimationScale(F)F", cancellable = true)
    public void auram$getWarningAnimationProgress(float f, CallbackInfoReturnable<Float> info) {
        if (!FastAnimations.enabled()) return;
        info.setReturnValue((this.clientSideStandAnimationO + f * (this.clientSideStandAnimation - this.clientSideStandAnimationO)) * 0.16666667F);
    }
}

// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim.entity;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.world.entity.animal.Wolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Wolf.class, priority = 900)
public class WolfEntityMixin {
    @Shadow
    private float shakeAnimO;
    @Shadow
    private float shakeAnim;
    @Shadow
    private float interestedAngleO;
    @Shadow
    private float interestedAngle;
    private static final float fifteenPi = 0.47123894F;

    @Inject(at = @At("HEAD"), method = "getBodyRollAngle(FF)F", cancellable = true)
    public void auram$getShakeAnimationProgress(float f, float g, CallbackInfoReturnable<Float> info) {
        if (!FastAnimations.enabled()) return;
        float h = (this.shakeAnimO + f * (this.shakeAnim - this.shakeAnimO) + g) * 0.5555F;
        if (h < 0.0F) {
            h = 0.0F;
        } else if (h > 1.0F) {
            h = 1.0F;
        }

        float hPi = h * (float) Math.PI;
        info.setReturnValue((float)(Math.sin(hPi) * Math.sin(hPi * 11.0F) * 0.47123894F));
    }

    @Inject(at = @At("HEAD"), method = "getHeadRollAngle(F)F", cancellable = true)
    public void auram$getBegAnimationProgress(float f, CallbackInfoReturnable<Float> info) {
        if (!FastAnimations.enabled()) return;
        info.setReturnValue((this.interestedAngleO + f * (this.interestedAngle - this.interestedAngleO)) * 0.47123894F);
    }
}

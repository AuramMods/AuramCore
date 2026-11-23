// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mth.class)
public class MathHelperMixin {
    @Inject(at = @At("HEAD"), method = "rotLerp(FFF)F", cancellable = true)
    private static void auram$lerpAngleDegrees(float f, float g, float h, CallbackInfoReturnable<Float> info) {
        if (!FastAnimations.enabled()) return;
        float angleCalc = (h - g) % 360.0F;
        if (angleCalc >= 180.0F) {
            angleCalc -= 360.0F;
        }

        if (angleCalc < -180.0F) {
            angleCalc += 360.0F;
        }

        info.setReturnValue(g + f * angleCalc);
    }
}

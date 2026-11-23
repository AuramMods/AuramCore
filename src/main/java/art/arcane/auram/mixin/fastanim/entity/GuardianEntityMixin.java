// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim.entity;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.world.entity.monster.Guardian;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Guardian.class)
public class GuardianEntityMixin {
    @Shadow
    private float clientSideTailAnimationO;
    @Shadow
    private float clientSideTailAnimation;

    @Inject(at = @At("HEAD"), method = "getTailAnimation(F)F", cancellable = true)
    public void auram$getTailAngle(float f, CallbackInfoReturnable<Float> info) {
        if (!FastAnimations.enabled()) return;
        info.setReturnValue(this.clientSideTailAnimationO + f * (this.clientSideTailAnimation - this.clientSideTailAnimationO));
    }
}

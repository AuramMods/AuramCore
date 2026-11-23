// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim.entity;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.world.entity.animal.allay.Allay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Allay.class)
public class AllayEntityMixin {
    @Shadow
    private float holdingItemAnimationTicks0;
    @Shadow
    private float holdingItemAnimationTicks;
    @Shadow
    private float spinningAnimationTicks0;
    @Shadow
    private float spinningAnimationTicks;

    @Inject(at = @At("HEAD"), method = "getHoldingItemAnimationProgress(F)F", cancellable = true)
    public void auram$getHoldingItemAnimationProgress(float f, CallbackInfoReturnable<Float> info) {
        if (!FastAnimations.enabled()) return;
        info.setReturnValue((this.holdingItemAnimationTicks0 + f * (this.holdingItemAnimationTicks - this.holdingItemAnimationTicks0)) * 0.2F);
    }

    @Inject(at = @At("HEAD"), method = "getSpinningProgress(F)F", cancellable = true)
    public void auram$getSpinningProgress(float f, CallbackInfoReturnable<Float> info) {
        if (!FastAnimations.enabled()) return;
        info.setReturnValue((this.spinningAnimationTicks0 + f * (this.spinningAnimationTicks - this.spinningAnimationTicks0)) * 0.06666667F);
    }
}

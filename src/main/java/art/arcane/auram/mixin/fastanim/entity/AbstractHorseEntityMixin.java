// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim.entity;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHorse.class)
public class AbstractHorseEntityMixin {
    @Shadow
    private float eatAnimO;
    @Shadow
    private float eatAnim;
    @Shadow
    private float standAnimO;
    @Shadow
    private float standAnim;
    @Shadow
    private float mouthAnimO;
    @Shadow
    private float mouthAnim;

    @Inject(at = @At("HEAD"), method = "getEatAnim(F)F", cancellable = true)
    public void auram$getEatingGrassAnimationProgress(float f, CallbackInfoReturnable<Float> info) {
        if (!FastAnimations.enabled()) return;
        info.setReturnValue(this.eatAnimO + f * (this.eatAnim - this.eatAnimO));
    }

    @Inject(at = @At("HEAD"), method = "getStandAnim(F)F", cancellable = true)
    public void auram$getAngryAnimationProgress(float f, CallbackInfoReturnable<Float> info) {
        if (!FastAnimations.enabled()) return;
        info.setReturnValue(this.standAnimO + f * (this.standAnim - this.standAnimO));
    }

    @Inject(at = @At("HEAD"), method = "getMouthAnim(F)F", cancellable = true)
    public void auram$getEatingAnimationProgress(float f, CallbackInfoReturnable<Float> info) {
        if (!FastAnimations.enabled()) return;
        info.setReturnValue(this.mouthAnimO + f * (this.mouthAnim - this.mouthAnimO));
    }
}

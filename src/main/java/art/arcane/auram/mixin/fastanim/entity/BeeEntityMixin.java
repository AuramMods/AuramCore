// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim.entity;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Bee.class)
public class BeeEntityMixin {
    @Shadow
    private float rollAmount;
    @Shadow
    private float rollAmountO;

    @Inject(at = @At("HEAD"), method = "getRollAmount(F)F", cancellable = true)
    public void auram$getBodyPitch(float tickDelta, CallbackInfoReturnable<Float> info) {
        if (!FastAnimations.enabled()) return;
        info.setReturnValue(this.rollAmountO + tickDelta * (this.rollAmount - this.rollAmountO));
    }
}

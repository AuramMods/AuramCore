// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim.entity;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Shulker.class)
public class ShulkerEntityMixin {
    @Shadow
    private float currentPeekAmountO;
    @Shadow
    private float currentPeekAmount;

    @Inject(at = @At("HEAD"), method = "getClientPeekAmount(F)F", cancellable = true)
    public void auram$getOpenProgress(float f, CallbackInfoReturnable<Float> info) {
        if (!FastAnimations.enabled()) return;
        info.setReturnValue(this.currentPeekAmountO + f * (this.currentPeekAmount - this.currentPeekAmountO));
    }
}

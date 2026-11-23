// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim.entity;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Warden.class)
public class WardenEntityMixin {
    @Shadow
    private int tendrilAnimationO;
    @Shadow
    private int tendrilAnimation;
    @Shadow
    private int heartAnimationO;
    @Shadow
    private int heartAnimation;

    @Inject(at = @At("HEAD"), method = "getTendrilAnimation(F)F", cancellable = true)
    public void auram$getTendrilPitch(float f, CallbackInfoReturnable<Float> info) {
        if (!FastAnimations.enabled()) return;
        info.setReturnValue((this.tendrilAnimationO + f * (this.tendrilAnimation - this.tendrilAnimationO)) * 0.1F);
    }

    @Inject(at = @At("HEAD"), method = "getHeartAnimation(F)F", cancellable = true)
    public void auram$getHeartPitch(float f, CallbackInfoReturnable<Float> info) {
        if (!FastAnimations.enabled()) return;
        info.setReturnValue((this.heartAnimationO + f * (this.heartAnimation - this.heartAnimationO)) * 0.1F);
    }
}

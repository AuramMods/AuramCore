// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.SalmonModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SalmonModel.class, priority = 900)
public class SalmonEntityModelMixin<T extends Entity> {
    @Final
    @Shadow
    private ModelPart bodyBack;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", cancellable = true)
    public void auram$setAngles(T entity, float limbAngle, float limbDistance, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        if (!entity.isInWater()) {
            this.bodyBack.yRot = (float)(-0.325F * Math.sin(1.02F * h));
        } else {
            this.bodyBack.yRot = (float)(-0.25 * Math.sin(0.6F * h));
        }
    }
}

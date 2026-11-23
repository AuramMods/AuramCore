// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.EvokerFangsModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EvokerFangsModel.class)
public class EvokerFangsEntityModelMixin<T extends Entity> {
    @Final
    @Shadow
    private ModelPart root;
    @Final
    @Shadow
    private ModelPart base;
    @Final
    @Shadow
    private ModelPart upperJaw;
    @Final
    @Shadow
    private ModelPart lowerJaw;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", cancellable = true)
    public void auram$setAngles(T entity, float limbAngle, float limbDistance, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float k = limbAngle * 2.0F;
        if (k > 1.0F) {
            k = 1.0F;
        }

        k = 1.0F - k * k * k;
        float k3Pi = k * 0.35F * (float) Math.PI;
        this.upperJaw.zRot = (float) Math.PI - k3Pi;
        this.lowerJaw.zRot = (float) Math.PI + k3Pi;
        float l = (limbAngle + (float)Math.sin(limbAngle * 2.7F)) * 7.2F;
        this.lowerJaw.y = this.upperJaw.y = this.base.y = 24.0F - l;
    }
}

// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.SnowGolemModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SnowGolemModel.class, priority = 900)
public class SnowGolemEntityModelMixin<T extends Entity> {
    @Final
    @Shadow
    private ModelPart root;
    @Final
    @Shadow
    private ModelPart upperBody;
    @Final
    @Shadow
    private ModelPart head;
    @Final
    @Shadow
    private ModelPart leftArm;
    @Final
    @Shadow
    private ModelPart rightArm;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", cancellable = true)
    public void auram$setAngles(T entity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float i180 = i * (float) (Math.PI / 180.0);
        this.head.yRot = i180;
        this.head.xRot = j * (float) (Math.PI / 180.0);
        float upperYaw = i180 * 0.25F;
        this.upperBody.yRot = upperYaw;
        float k = (float)Math.sin(this.upperBody.yRot);
        float l = (float)Math.sin(this.upperBody.yRot + (float) (Math.PI / 2));
        this.leftArm.yRot = upperYaw;
        this.rightArm.yRot = upperYaw + (float) Math.PI;
        float l5 = l * 5.0F;
        float k5 = k * 5.0F;
        this.leftArm.x = l5;
        this.leftArm.z = -k5;
        this.rightArm.x = -l5;
        this.rightArm.z = k5;
    }
}

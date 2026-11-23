// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndermanModel.class)
public abstract class EndermanEntityModelMixin<T extends LivingEntity> extends HumanoidModel<LivingEntity> {
    @Shadow
    public boolean carrying;
    @Shadow
    public boolean creepy;

    public EndermanEntityModelMixin(ModelPart modelPart) {
        super(modelPart);
    }

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", cancellable = true)
    public void auram$setAngles(LivingEntity livingEntity, float limbAngle, float limbDistance, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        super.setupAnim(livingEntity, limbAngle, limbDistance, h, i, j);
        this.body.xRot = 0.0F;
        this.body.y = -14.0F;
        this.body.z = -0.0F;
        this.rightArm.xRot *= 0.5F;
        this.leftArm.xRot *= 0.5F;
        this.rightLeg.xRot *= 0.5F;
        this.leftLeg.xRot *= 0.5F;
        if (this.rightArm.xRot > 0.4F) {
            this.rightArm.xRot = 0.4F;
        }

        if (this.leftArm.xRot > 0.4F) {
            this.leftArm.xRot = 0.4F;
        }

        if (this.rightArm.xRot < -0.4F) {
            this.rightArm.xRot = -0.4F;
        }

        if (this.leftArm.xRot < -0.4F) {
            this.leftArm.xRot = -0.4F;
        }

        if (this.rightLeg.xRot > 0.4F) {
            this.rightLeg.xRot = 0.4F;
        }

        if (this.leftLeg.xRot > 0.4F) {
            this.leftLeg.xRot = 0.4F;
        }

        if (this.rightLeg.xRot < -0.4F) {
            this.rightLeg.xRot = -0.4F;
        }

        if (this.leftLeg.xRot < -0.4F) {
            this.leftLeg.xRot = -0.4F;
        }

        if (this.carrying) {
            this.rightArm.xRot = -0.5F;
            this.leftArm.xRot = -0.5F;
            this.rightArm.zRot = 0.05F;
            this.leftArm.zRot = -0.05F;
        }

        this.rightLeg.z = 0.0F;
        this.leftLeg.z = 0.0F;
        this.rightLeg.y = -5.0F;
        this.leftLeg.y = -5.0F;
        this.head.z = -0.0F;
        this.head.y = -13.0F;
        this.hat.x = this.head.x;
        this.hat.y = this.head.y;
        this.hat.z = this.head.z;
        this.hat.xRot = this.head.xRot;
        this.hat.yRot = this.head.yRot;
        this.hat.zRot = this.head.zRot;
        if (this.creepy) {
            this.head.y -= 5.0F;
        }

        this.rightArm.setPos(-5.0F, -12.0F, 0.0F);
        this.leftArm.setPos(5.0F, -12.0F, 0.0F);
    }
}

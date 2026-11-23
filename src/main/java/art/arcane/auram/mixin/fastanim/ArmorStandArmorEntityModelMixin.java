// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.ArmorStandArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Rotations;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorStandArmorModel.class)
public abstract class ArmorStandArmorEntityModelMixin extends HumanoidModel<ArmorStand> {
    public ArmorStandArmorEntityModelMixin(ModelPart modelPart) {
        super(modelPart);
    }

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/decoration/ArmorStand;FFFFF)V", cancellable = true)
    public void auram$setAngles(ArmorStand armorStandEntity, float limbAngle, float limbDistance, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        Rotations head = armorStandEntity.getHeadPose();
        this.head.xRot = (float) (Math.PI / 180.0) * head.getX();
        this.head.yRot = (float) (Math.PI / 180.0) * head.getY();
        this.head.zRot = (float) (Math.PI / 180.0) * head.getZ();
        Rotations body = armorStandEntity.getBodyPose();
        this.body.xRot = (float) (Math.PI / 180.0) * body.getX();
        this.body.yRot = (float) (Math.PI / 180.0) * body.getY();
        this.body.zRot = (float) (Math.PI / 180.0) * body.getZ();
        Rotations leftArm = armorStandEntity.getLeftArmPose();
        this.leftArm.xRot = (float) (Math.PI / 180.0) * leftArm.getX();
        this.leftArm.yRot = (float) (Math.PI / 180.0) * leftArm.getY();
        this.leftArm.zRot = (float) (Math.PI / 180.0) * leftArm.getZ();
        Rotations rightArm = armorStandEntity.getRightArmPose();
        this.rightArm.xRot = (float) (Math.PI / 180.0) * rightArm.getX();
        this.rightArm.yRot = (float) (Math.PI / 180.0) * rightArm.getY();
        this.rightArm.zRot = (float) (Math.PI / 180.0) * rightArm.getZ();
        Rotations leftLeg = armorStandEntity.getLeftLegPose();
        this.leftLeg.xRot = (float) (Math.PI / 180.0) * leftLeg.getX();
        this.leftLeg.yRot = (float) (Math.PI / 180.0) * leftLeg.getY();
        this.leftLeg.zRot = (float) (Math.PI / 180.0) * leftLeg.getZ();
        Rotations rightLeg = armorStandEntity.getRightLegPose();
        this.rightLeg.xRot = (float) (Math.PI / 180.0) * rightLeg.getX();
        this.rightLeg.yRot = (float) (Math.PI / 180.0) * rightLeg.getY();
        this.rightLeg.zRot = (float) (Math.PI / 180.0) * rightLeg.getZ();
        this.hat.copyFrom(this.head);
    }
}

// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.ArmorStandArmorModel;
import net.minecraft.client.model.ArmorStandModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Rotations;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorStandModel.class)
public abstract class ArmorStandEntityModelMixin extends ArmorStandArmorModel {
    @Final
    @Shadow
    private ModelPart rightBodyStick;
    @Final
    @Shadow
    private ModelPart leftBodyStick;
    @Final
    @Shadow
    private ModelPart shoulderStick;
    @Final
    @Shadow
    private ModelPart basePlate;

    public ArmorStandEntityModelMixin(ModelPart modelPart) {
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
        float bodyPitch = (float) (Math.PI / 180.0) * body.getX();
        float bodyYaw = (float) (Math.PI / 180.0) * body.getY();
        float bodyRoll = (float) (Math.PI / 180.0) * body.getZ();
        this.body.xRot = bodyPitch;
        this.body.yRot = bodyYaw;
        this.body.zRot = bodyRoll;
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
        boolean showArms = armorStandEntity.isShowArms();
        this.leftArm.visible = showArms;
        this.rightArm.visible = showArms;
        this.basePlate.visible = !armorStandEntity.isNoBasePlate();
        this.rightBodyStick.xRot = bodyPitch;
        this.rightBodyStick.yRot = bodyYaw;
        this.rightBodyStick.zRot = bodyRoll;
        this.leftBodyStick.xRot = bodyPitch;
        this.leftBodyStick.yRot = bodyYaw;
        this.leftBodyStick.zRot = bodyRoll;
        this.shoulderStick.xRot = bodyPitch;
        this.shoulderStick.yRot = bodyYaw;
        this.shoulderStick.zRot = bodyRoll;
    }
}

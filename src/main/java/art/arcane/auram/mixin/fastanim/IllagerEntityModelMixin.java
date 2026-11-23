// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.AbstractIllager.IllagerArmPose;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IllagerModel.class)
public abstract class IllagerEntityModelMixin<T extends AbstractIllager> extends HierarchicalModel<T> implements ArmedModel, HeadedModel {
    @Final
    @Shadow
    private ModelPart root;
    @Final
    @Shadow
    private ModelPart head;
    @Final
    @Shadow
    private ModelPart hat;
    @Final
    @Shadow
    private ModelPart arms;
    @Final
    @Shadow
    private ModelPart leftLeg;
    @Final
    @Shadow
    private ModelPart rightLeg;
    @Final
    @Shadow
    private ModelPart rightArm;
    @Final
    @Shadow
    private ModelPart leftArm;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/monster/AbstractIllager;FFFFF)V", cancellable = true)
    public void auram$setAngles(T illagerEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        this.head.yRot = i * (float) (Math.PI / 180.0);
        this.head.xRot = j * (float) (Math.PI / 180.0);
        if (this.riding) {
            this.rightArm.xRot = (float) (-Math.PI / 5);
            this.rightArm.yRot = 0.0F;
            this.rightArm.zRot = 0.0F;
            this.leftArm.xRot = (float) (-Math.PI / 5);
            this.leftArm.yRot = 0.0F;
            this.leftArm.zRot = 0.0F;
            this.rightLeg.xRot = -1.4137167F;
            this.rightLeg.yRot = (float) (Math.PI / 10);
            this.rightLeg.zRot = 0.07853982F;
            this.leftLeg.xRot = -1.4137167F;
            this.leftLeg.yRot = (float) (-Math.PI / 10);
            this.leftLeg.zRot = -0.07853982F;
        } else {
            float fastAngle = f * 0.6662F;
            float oneFourG = 0.7F * g;
            float cosOne = (float)Math.sin(fastAngle + (float) (Math.PI / 2));
            float cosTwo = (float)Math.sin(fastAngle + (float) Math.PI + (float) (Math.PI / 2));
            this.rightLeg.xRot = cosOne * oneFourG;
            this.leftArm.xRot = cosOne;
            this.rightArm.xRot = cosTwo;
            this.leftLeg.xRot = cosTwo * oneFourG;
            this.rightArm.yRot = 0.0F;
            this.rightArm.zRot = 0.0F;
            this.leftArm.yRot = 0.0F;
            this.leftArm.zRot = 0.0F;
            this.rightLeg.yRot = 0.0F;
            this.rightLeg.zRot = 0.0F;
            this.leftLeg.yRot = 0.0F;
            this.leftLeg.zRot = 0.0F;
        }

        IllagerArmPose state = illagerEntity.getArmPose();
        if (state == IllagerArmPose.ATTACKING) {
            if (illagerEntity.getMainHandItem().isEmpty()) {
                AnimationUtils.animateZombieArms(this.leftArm, this.rightArm, true, this.attackTime, h);
            } else {
                AnimationUtils.swingWeaponDown(this.rightArm, this.leftArm, illagerEntity, this.attackTime, h);
            }
        } else if (state == IllagerArmPose.SPELLCASTING) {
            this.rightArm.z = 0.0F;
            this.rightArm.x = -5.0F;
            this.leftArm.z = 0.0F;
            this.leftArm.x = 5.0F;
            float cos = (float)Math.sin(h * 0.6662F + (float) (Math.PI / 2)) * 0.25F;
            this.rightArm.xRot = cos;
            this.leftArm.xRot = cos;
            this.rightArm.zRot = (float) (Math.PI * 3.0 / 4.0);
            this.leftArm.zRot = (float) (-Math.PI * 3.0 / 4.0);
            this.rightArm.yRot = 0.0F;
            this.leftArm.yRot = 0.0F;
        } else if (state == IllagerArmPose.BOW_AND_ARROW) {
            this.rightArm.yRot = -0.1F + this.head.yRot;
            this.rightArm.xRot = (float) (-Math.PI / 2) + this.head.xRot;
            this.leftArm.xRot = -0.9424779F + this.head.xRot;
            this.leftArm.yRot = this.head.yRot - 0.4F;
            this.leftArm.zRot = (float) (Math.PI / 2);
        } else if (state == IllagerArmPose.CROSSBOW_HOLD) {
            AnimationUtils.animateCrossbowHold(this.rightArm, this.leftArm, this.head, true);
        } else if (state == IllagerArmPose.CROSSBOW_CHARGE) {
            AnimationUtils.animateCrossbowCharge(this.rightArm, this.leftArm, illagerEntity, true);
        } else if (state == IllagerArmPose.CELEBRATING) {
            this.rightArm.z = 0.0F;
            this.rightArm.x = -5.0F;
            float cos = (float)Math.sin(h * 0.6662F + (float) (Math.PI / 2)) * 0.05F;
            this.rightArm.xRot = cos;
            this.rightArm.zRot = 2.670354F;
            this.rightArm.yRot = 0.0F;
            this.leftArm.z = 0.0F;
            this.leftArm.x = 5.0F;
            this.leftArm.xRot = cos;
            this.leftArm.zRot = (float) (-Math.PI * 3.0 / 4.0);
            this.leftArm.yRot = 0.0F;
        }

        boolean bl;
        this.arms.visible = bl = state == IllagerArmPose.CROSSED;
        this.leftArm.visible = !bl;
        this.rightArm.visible = !bl;
    }

    @Shadow
    private ModelPart getArm(HumanoidArm arm) {
        return arm == HumanoidArm.LEFT ? this.leftArm : this.rightArm;
    }

    public ModelPart getHead() {
        return this.head;
    }

    public void translateToHand(HumanoidArm arm, PoseStack matrixStack) {
        this.getArm(arm).translateAndRotate(matrixStack);
    }

    public ModelPart root() {
        return this.root;
    }
}

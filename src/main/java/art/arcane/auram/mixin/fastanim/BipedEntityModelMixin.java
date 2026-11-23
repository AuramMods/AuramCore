// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class BipedEntityModelMixin<T extends LivingEntity> extends AgeableListModel<T> implements ArmedModel, HeadedModel {
    @Final
    @Shadow
    public ModelPart head;
    @Final
    @Shadow
    public ModelPart hat;
    @Final
    @Shadow
    public ModelPart body;
    @Final
    @Shadow
    public ModelPart rightArm;
    @Final
    @Shadow
    public ModelPart leftArm;
    @Final
    @Shadow
    public ModelPart rightLeg;
    @Final
    @Shadow
    public ModelPart leftLeg;
    @Shadow
    public ArmPose leftArmPose;
    @Shadow
    public ArmPose rightArmPose;
    @Shadow
    public boolean crouching;
    @Shadow
    public float swimAmount;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", cancellable = true)
    public void auram$setAngles(T livingEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        boolean roll4 = livingEntity.getFallFlyingTicks() > 4;
        boolean swimming = livingEntity.isVisuallySwimming();
        this.head.yRot = i * (float) (Math.PI / 180.0);
        if (roll4) {
            this.head.xRot = (float) (-Math.PI / 4);
        } else if (this.swimAmount > 0.0F) {
            this.head.xRot = swimming
                ? this.rotlerpRad(this.swimAmount, this.head.xRot, (float) (-Math.PI / 4))
                : this.rotlerpRad(this.swimAmount, this.head.xRot, j * (float) (Math.PI / 180.0));
        } else {
            this.head.xRot = j * (float) (Math.PI / 180.0);
        }

        this.body.yRot = 0.0F;
        this.rightArm.z = 0.0F;
        this.rightArm.x = -5.0F;
        this.leftArm.z = 0.0F;
        this.leftArm.x = 5.0F;
        float k = 1.0F;
        if (roll4) {
            k = (float)livingEntity.getDeltaMovement().lengthSqr();
            k /= 0.2F;
            k *= k * k;
        }

        if (k < 1.0F) {
            k = 1.0F;
        }

        float fastf = f * 0.6662F;
        float twoLimbFiveK = 2.0F * g * 0.5F / k;
        float oneFourLimbK = 1.4F * g / k;
        float cosOne = (float)Math.sin(fastf + (float) (Math.PI / 2));
        float cosTwo = (float)Math.sin(fastf + (float) Math.PI + (float) (Math.PI / 2));
        this.rightArm.xRot = cosTwo * twoLimbFiveK;
        this.leftArm.xRot = cosOne * twoLimbFiveK;
        this.rightArm.zRot = 0.0F;
        this.leftArm.zRot = 0.0F;
        this.rightLeg.xRot = cosOne * oneFourLimbK;
        this.leftLeg.xRot = cosTwo * oneFourLimbK;
        this.rightLeg.yRot = 0.0F;
        this.leftLeg.yRot = 0.0F;
        this.rightLeg.zRot = 0.0F;
        this.leftLeg.zRot = 0.0F;
        if (this.riding) {
            this.rightArm.xRot -= (float) (Math.PI / 5);
            this.leftArm.xRot -= (float) (Math.PI / 5);
            this.rightLeg.xRot = -1.4137167F;
            this.rightLeg.yRot = (float) (Math.PI / 10);
            this.rightLeg.zRot = 0.07853982F;
            this.leftLeg.xRot = -1.4137167F;
            this.leftLeg.yRot = (float) (-Math.PI / 10);
            this.leftLeg.zRot = -0.07853982F;
        }

        this.rightArm.yRot = 0.0F;
        this.leftArm.yRot = 0.0F;
        boolean rightHanded = livingEntity.getMainArm() == HumanoidArm.RIGHT;
        if (livingEntity.isUsingItem()) {
            boolean bl4 = livingEntity.getUsedItemHand() == InteractionHand.MAIN_HAND;
            if (bl4 == rightHanded) {
                this.poseRightArm(livingEntity);
            } else {
                this.poseLeftArm(livingEntity);
            }
        } else {
            boolean bl4 = rightHanded ? this.leftArmPose.isTwoHanded() : this.rightArmPose.isTwoHanded();
            if (rightHanded != bl4) {
                this.poseLeftArm(livingEntity);
                this.poseRightArm(livingEntity);
            } else {
                this.poseRightArm(livingEntity);
                this.poseLeftArm(livingEntity);
            }
        }

        this.setupAttackAnimation(livingEntity, h);
        if (this.crouching) {
            this.body.xRot = 0.5F;
            this.rightArm.xRot += 0.4F;
            this.leftArm.xRot += 0.4F;
            this.rightLeg.z = 4.0F;
            this.leftLeg.z = 4.0F;
            this.rightLeg.y = 12.2F;
            this.leftLeg.y = 12.2F;
            this.head.y = 4.2F;
            this.body.y = 3.2F;
            this.leftArm.y = 5.2F;
            this.rightArm.y = 5.2F;
        } else {
            this.body.xRot = 0.0F;
            this.rightLeg.z = 0.1F;
            this.leftLeg.z = 0.1F;
            this.rightLeg.y = 12.0F;
            this.leftLeg.y = 12.0F;
            this.head.y = 0.0F;
            this.body.y = 0.0F;
            this.leftArm.y = 2.0F;
            this.rightArm.y = 2.0F;
        }

        if (this.rightArmPose != ArmPose.SPYGLASS) {
            AnimationUtils.bobModelPart(this.rightArm, h, 1.0F);
        }

        if (this.leftArmPose != ArmPose.SPYGLASS) {
            AnimationUtils.bobModelPart(this.leftArm, h, -1.0F);
        }

        if (this.swimAmount > 0.0F) {
            float l = f % 26.0F;
            HumanoidArm arm = this.getAttackArm(livingEntity);
            float m = arm == HumanoidArm.RIGHT && this.attackTime > 0.0F ? 0.0F : this.swimAmount;
            float n = arm == HumanoidArm.LEFT && this.attackTime > 0.0F ? 0.0F : this.swimAmount;
            if (!livingEntity.isUsingItem()) {
                if (l < 14.0F) {
                    float methodL = 1.8707964F * (-65.0F * l + l * l) / -714.0F;
                    this.leftArm.xRot = this.rotlerpRad(n, this.leftArm.xRot, 0.0F);
                    this.rightArm.xRot = this.rightArm.xRot + m * (0.0F - this.rightArm.xRot);
                    this.leftArm.yRot = this.rotlerpRad(n, this.leftArm.yRot, (float) Math.PI);
                    this.rightArm.yRot = this.rightArm.yRot + m * ((float) Math.PI - this.rightArm.yRot);
                    this.leftArm.zRot = this.rotlerpRad(n, this.leftArm.zRot, (float) Math.PI + methodL);
                    this.rightArm.zRot = this.rightArm.zRot + m * ((float) Math.PI - methodL - this.rightArm.zRot);
                } else if (l >= 14.0F && l < 22.0F) {
                    float o = (l - 14.0F) / 8.0F;
                    float one5O = (float) (Math.PI / 2) * o;
                    float one8O = 1.8707964F * o;
                    this.leftArm.xRot = this.rotlerpRad(n, this.leftArm.xRot, one5O);
                    this.rightArm.xRot = this.rightArm.xRot + m * (one5O - this.rightArm.xRot);
                    this.leftArm.yRot = this.rotlerpRad(n, this.leftArm.yRot, (float) Math.PI);
                    this.rightArm.yRot = this.rightArm.yRot + m * ((float) Math.PI - this.rightArm.yRot);
                    this.leftArm.zRot = this.rotlerpRad(n, this.leftArm.zRot, 5.012389F - one8O);
                    this.rightArm.zRot = this.rightArm.zRot + m * (1.2707963F + one8O - this.rightArm.zRot);
                } else if (l >= 22.0F && l < 26.0F) {
                    float o = (l - 22.0F) / 4.0F;
                    float one5O = (float) (Math.PI / 2) - (float) (Math.PI / 2) * o;
                    this.leftArm.xRot = this.rotlerpRad(n, this.leftArm.xRot, one5O);
                    this.rightArm.xRot = this.rightArm.xRot + m * (one5O - this.rightArm.xRot);
                    this.leftArm.yRot = this.rotlerpRad(n, this.leftArm.yRot, (float) Math.PI);
                    this.rightArm.yRot = this.rightArm.yRot + m * ((float) Math.PI - this.rightArm.yRot);
                    this.leftArm.zRot = this.rotlerpRad(n, this.leftArm.zRot, (float) Math.PI);
                    this.rightArm.zRot = this.rightArm.zRot + m * ((float) Math.PI - this.rightArm.zRot);
                }
            }

            float limb3 = f * 0.33333334F;
            this.leftLeg.xRot = this.leftLeg.xRot
                + this.swimAmount * (0.3F * (float)Math.sin(limb3 + (float) Math.PI + (float) (Math.PI / 2)) - this.leftLeg.xRot);
            this.rightLeg.xRot = this.rightLeg.xRot
                + this.swimAmount * (0.3F * (float)Math.sin(limb3 + (float) (Math.PI / 2)) - this.rightLeg.xRot);
        }

        this.hat.copyFrom(this.head);
    }

    @Inject(at = @At("HEAD"), method = "poseRightArm(Lnet/minecraft/world/entity/LivingEntity;)V", cancellable = true)
    private void auram$positionRightArm(T livingEntity, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        switch (this.rightArmPose) {
            case EMPTY:
                this.rightArm.yRot = 0.0F;
                break;
            case BLOCK:
                this.rightArm.xRot = this.rightArm.xRot * 0.5F - 0.9424779F;
                this.rightArm.yRot = (float) (-Math.PI / 6);
                break;
            case ITEM:
                this.rightArm.xRot = this.rightArm.xRot * 0.5F - (float) (Math.PI / 10);
                this.rightArm.yRot = 0.0F;
                break;
            case THROW_SPEAR:
                this.rightArm.xRot = this.rightArm.xRot * 0.5F - (float) Math.PI;
                this.rightArm.yRot = 0.0F;
                break;
            case BOW_AND_ARROW:
                float yaws = 0.1F + this.head.yRot;
                this.rightArm.yRot = yaws;
                this.leftArm.yRot = yaws + 0.4F;
                float pitches = (float) (-Math.PI / 2) + this.head.xRot;
                this.rightArm.xRot = pitches;
                this.leftArm.xRot = pitches;
                break;
            case CROSSBOW_CHARGE:
                AnimationUtils.animateCrossbowCharge(this.rightArm, this.leftArm, livingEntity, true);
                break;
            case CROSSBOW_HOLD:
                AnimationUtils.animateCrossbowHold(this.rightArm, this.leftArm, this.head, true);
                break;
            case SPYGLASS:
                this.rightArm.xRot = Mth.clamp(
                    this.head.xRot - 1.9198622F - (livingEntity.isCrouching() ? (float) (Math.PI / 12) : 0.0F), -2.4F, 3.3F
                );
                this.rightArm.yRot = this.head.yRot - (float) (Math.PI / 12);
                break;
            case TOOT_HORN:
                this.rightArm.xRot = Mth.clamp(this.head.xRot, -1.2F, 1.2F) - 1.4835298F;
                this.rightArm.yRot = this.head.yRot - (float) (Math.PI / 6);
        }
    }

    @Inject(at = @At("HEAD"), method = "poseLeftArm(Lnet/minecraft/world/entity/LivingEntity;)V", cancellable = true)
    private void auram$positionLeftArm(T livingEntity, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        switch (this.leftArmPose) {
            case EMPTY:
                this.leftArm.yRot = 0.0F;
                break;
            case BLOCK:
                this.leftArm.xRot = this.leftArm.xRot * 0.5F - 0.9424779F;
                this.leftArm.yRot = (float) (Math.PI / 6);
                break;
            case ITEM:
                this.leftArm.xRot = this.leftArm.xRot * 0.5F - (float) (Math.PI / 10);
                this.leftArm.yRot = 0.0F;
                break;
            case THROW_SPEAR:
                this.leftArm.xRot = this.leftArm.xRot * 0.5F - (float) Math.PI;
                this.leftArm.yRot = 0.0F;
                break;
            case BOW_AND_ARROW:
                float yaws = 0.1F + this.head.yRot;
                this.rightArm.yRot = -yaws - 0.4F;
                this.leftArm.yRot = yaws;
                float pitches = (float) (-Math.PI / 2) + this.head.xRot;
                this.rightArm.xRot = pitches;
                this.leftArm.xRot = pitches;
                break;
            case CROSSBOW_CHARGE:
                AnimationUtils.animateCrossbowCharge(this.rightArm, this.leftArm, livingEntity, false);
                break;
            case CROSSBOW_HOLD:
                AnimationUtils.animateCrossbowHold(this.rightArm, this.leftArm, this.head, false);
                break;
            case SPYGLASS:
                this.leftArm.xRot = Mth.clamp(
                    this.head.xRot - 1.9198622F - (livingEntity.isCrouching() ? (float) (Math.PI / 12) : 0.0F), -2.4F, 3.3F
                );
                this.leftArm.yRot = this.head.yRot + (float) (Math.PI / 12);
                break;
            case TOOT_HORN:
                this.leftArm.xRot = Mth.clamp(this.head.xRot, -1.2F, 1.2F) - 1.4835298F;
                this.leftArm.yRot = this.head.yRot + (float) (Math.PI / 6);
        }
    }

    @Inject(at = @At("HEAD"), method = "setupAttackAnimation(Lnet/minecraft/world/entity/LivingEntity;F)V", cancellable = true)
    public void auram$animateArms(T livingEntity, float f, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        if (!(this.attackTime <= 0.0F)) {
            HumanoidArm arm = this.getAttackArm(livingEntity);
            ModelPart modelPart = this.getArm(arm);
            float g = this.attackTime;
            this.body.yRot = (float)Math.sin((float)Math.sqrt(g) * (float) (Math.PI * 2)) * 0.2F;
            if (arm == HumanoidArm.LEFT) {
                this.body.yRot *= -1.0F;
            }

            float sin = (float)Math.sin(this.body.yRot) * 5.0F;
            float cos = (float)Math.sin(this.body.yRot + (float) (Math.PI / 2)) * 5.0F;
            this.rightArm.z = sin;
            this.rightArm.x = -cos;
            this.leftArm.z = -sin;
            this.leftArm.x = cos;
            this.rightArm.yRot = this.rightArm.yRot + this.body.yRot;
            this.leftArm.yRot = this.leftArm.yRot + this.body.yRot;
            this.leftArm.xRot = this.leftArm.xRot + this.body.yRot;
            g = 1.0F - this.attackTime;
            g *= g;
            g *= g;
            g = 1.0F - g;
            float sinHand3 = (float)Math.sin(this.attackTime * (float) Math.PI);
            float i = sinHand3 * -(this.head.xRot - 0.7F) * 0.75F;
            modelPart.xRot = modelPart.xRot - ((float)Math.sin(g * (float) Math.PI) * 1.2F + i);
            modelPart.yRot = modelPart.yRot + this.body.yRot * 2.0F;
            modelPart.zRot += sinHand3 * -0.4F;
        }
    }

    @Shadow
    private void poseLeftArm(T livingEntity) {
    }

    @Shadow
    public ModelPart getArm(HumanoidArm arm) {
        throw new AssertionError("Mixin injection failed - FastAnim BipedEntityModelMixin");
    }

    @Shadow
    public float rotlerpRad(float f, float g, float h) {
        throw new AssertionError("Mixin injection failed - FastAnim BipedEntityModelMixin");
    }

    @Shadow
    private HumanoidArm getAttackArm(T livingEntity) {
        throw new AssertionError("Mixin injection failed - FastAnim BipedEntityModelMixin");
    }

    @Shadow
    public void setupAttackAnimation(T livingEntity, float f) {
    }

    @Shadow
    private void poseRightArm(T livingEntity) {
    }
}

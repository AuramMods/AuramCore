// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.PiglinModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinArmPose;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PiglinModel.class, priority = 900)
public abstract class PiglinEntityModelMixin<T extends Mob> extends PlayerModel<T> {
    @Final
    @Shadow
    public ModelPart rightEar;
    @Final
    @Shadow
    private ModelPart leftEar;
    @Final
    @Shadow
    private PartPose bodyDefault;
    @Final
    @Shadow
    private PartPose headDefault;
    @Final
    @Shadow
    private PartPose leftArmDefault;
    @Final
    @Shadow
    private PartPose rightArmDefault;

    public PiglinEntityModelMixin(ModelPart modelPart, boolean bl) {
        super(modelPart, bl);
    }

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/Mob;FFFFF)V", cancellable = true)
    public void auram$setAngles(T mobEntity, float limbAngle, float limbDistance, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        this.body.loadPose(this.bodyDefault);
        this.head.loadPose(this.headDefault);
        this.leftArm.loadPose(this.leftArmDefault);
        this.rightArm.loadPose(this.rightArmDefault);
        super.setupAnim(mobEntity, limbAngle, limbDistance, h, i, j);
        float l = h * 0.1F + limbAngle * 0.5F;
        float m = 0.08F + limbDistance * 0.4F;
        this.leftEar.zRot = (float) (-Math.PI / 6) - (float)Math.sin(l * 1.2F + (float) (Math.PI / 2)) * m;
        this.rightEar.zRot = (float) (Math.PI / 6) + (float)Math.sin(l + (float) (Math.PI / 2)) * m;
        if (mobEntity instanceof AbstractPiglin abstractPiglinEntity) {
            PiglinArmPose piglinActivity = abstractPiglinEntity.getArmPose();
            if (piglinActivity == PiglinArmPose.DANCING) {
                float n = h * 0.016666F;
                float n30 = n * 30.0F;
                this.rightEar.zRot = (float) (Math.PI / 6) + (float) (Math.PI / 180.0) * (float)Math.sin(n30) * 10.0F;
                this.leftEar.zRot = (float) (-Math.PI / 6) - (float) (Math.PI / 180.0) * (float)Math.sin(n30 + (float) (Math.PI / 2)) * 10.0F;
                this.head.x = (float)Math.sin(n * 10.0F);
                float n40 = n * 40.0F;
                float sin = (float)Math.sin(n40);
                this.head.y = sin + 0.4F;
                this.rightArm.zRot = (float) (Math.PI / 180.0) * (70.0F + (float)Math.sin(n40 + (float) (Math.PI / 2)) * 10.0F);
                this.leftArm.zRot = this.rightArm.zRot * -1.0F;
                float armPivot = sin * 0.5F + 1.5F;
                this.rightArm.y = armPivot;
                this.leftArm.y = armPivot;
                this.body.y = sin * 0.35F;
            } else if (piglinActivity == PiglinArmPose.ATTACKING_WITH_MELEE_WEAPON && this.attackTime == 0.0F) {
                this.holdWeaponHigh(mobEntity);
            } else if (piglinActivity == PiglinArmPose.CROSSBOW_HOLD) {
                AnimationUtils.animateCrossbowHold(this.rightArm, this.leftArm, this.head, !mobEntity.isLeftHanded());
            } else if (piglinActivity == PiglinArmPose.CROSSBOW_CHARGE) {
                AnimationUtils.animateCrossbowCharge(this.rightArm, this.leftArm, mobEntity, !mobEntity.isLeftHanded());
            } else if (piglinActivity == PiglinArmPose.ADMIRING_ITEM) {
                this.head.xRot = 0.5F;
                this.head.yRot = 0.0F;
                if (mobEntity.isLeftHanded()) {
                    this.rightArm.yRot = -0.5F;
                    this.rightArm.xRot = -0.9F;
                } else {
                    this.leftArm.yRot = 0.5F;
                    this.leftArm.xRot = -0.9F;
                }
            }
        } else if (mobEntity.getType() == EntityType.ZOMBIFIED_PIGLIN) {
            AnimationUtils.animateZombieArms(this.leftArm, this.rightArm, mobEntity.isAggressive(), this.attackTime, h);
        }

        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.jacket.copyFrom(this.body);
        this.hat.copyFrom(this.head);
    }

    @Shadow
    public void setupAttackAnimation(T mobEntity, float f) {
    }

    @Shadow
    private void holdWeaponHigh(T mobEntity) {
    }
}

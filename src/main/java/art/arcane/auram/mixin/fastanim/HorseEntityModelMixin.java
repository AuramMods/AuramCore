// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HorseModel.class)
public class HorseEntityModelMixin<T extends AbstractHorse> {
    @Final
    @Shadow
    public ModelPart body;
    @Final
    @Shadow
    public ModelPart headParts;
    @Final
    @Shadow
    private ModelPart rightHindLeg;
    @Final
    @Shadow
    private ModelPart leftHindLeg;
    @Final
    @Shadow
    private ModelPart rightFrontLeg;
    @Final
    @Shadow
    private ModelPart leftFrontLeg;
    @Final
    @Shadow
    private ModelPart rightHindBabyLeg;
    @Final
    @Shadow
    private ModelPart leftHindBabyLeg;
    @Final
    @Shadow
    private ModelPart rightFrontBabyLeg;
    @Final
    @Shadow
    private ModelPart leftFrontBabyLeg;
    @Final
    @Shadow
    private ModelPart tail;

    @Inject(at = @At("HEAD"), method = "prepareMobModel(Lnet/minecraft/world/entity/animal/horse/AbstractHorse;FFF)V", cancellable = true)
    public void auram$animateModel(T abstractHorseEntity, float limbAngle, float limbDistance, float h, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float bodyYaw = Mth.rotLerp(abstractHorseEntity.yBodyRotO, abstractHorseEntity.yBodyRot, h);
        float headYaw = Mth.rotLerp(abstractHorseEntity.yHeadRotO, abstractHorseEntity.yHeadRot, h);
        float k = abstractHorseEntity.xRotO + h * (abstractHorseEntity.getXRot() - abstractHorseEntity.xRotO);
        float headMinusBodyYaws = headYaw - bodyYaw;
        float m = k * (float) (Math.PI / 180.0);
        if (headMinusBodyYaws > 20.0F) {
            headMinusBodyYaws = 20.0F;
        }

        if (headMinusBodyYaws < -20.0F) {
            headMinusBodyYaws = -20.0F;
        }

        if (limbDistance > 0.2F) {
            m += (float)Math.sin(limbAngle * 0.4F + (float) (Math.PI / 2)) * 0.15F * limbDistance;
        }

        float eatingProgress = abstractHorseEntity.getEatAnim(h);
        float angryProgress = abstractHorseEntity.getStandAnim(h);
        float p = 1.0F - angryProgress;
        float q = abstractHorseEntity.getMouthAnim(h);
        boolean bl = abstractHorseEntity.tailCounter != 0;
        float age = abstractHorseEntity.tickCount + h;
        this.body.xRot = 0.0F;
        this.headParts.xRot = (float) (Math.PI / 6) + m;
        float headMinBodYawsRadians = headMinusBodyYaws * (float) (Math.PI / 180.0);
        this.headParts.yRot = headMinBodYawsRadians;
        float s = abstractHorseEntity.isInWater() ? 0.2F : 1.0F;
        float t = Mth.cos(s * limbAngle * 0.6662F + (float) Math.PI);
        float u = t * 0.8F * limbDistance;
        float oneMathMaxEatingProg = 1.0F - Math.max(angryProgress, eatingProgress);
        float sinAge = Mth.sin(age);
        this.headParts.xRot = angryProgress * ((float) (Math.PI / 12) + eatingProgress)
            + eatingProgress * (2.1816616F + sinAge * 0.05F)
            + oneMathMaxEatingProg * ((float) (Math.PI / 6) + m + q * sinAge * 0.05F);
        this.headParts.yRot = angryProgress * headMinBodYawsRadians + oneMathMaxEatingProg * this.headParts.yRot;
        float angry4 = angryProgress * -4.0F;
        this.headParts.y = angry4 + eatingProgress * 11.0F + oneMathMaxEatingProg * 4.0F;
        this.headParts.z = angry4 + eatingProgress * -12.0F + oneMathMaxEatingProg * -12.0F;
        this.body.xRot = angryProgress * (float) (-Math.PI / 4) + p * this.body.xRot;
        float w = (float) (Math.PI / 12) * angryProgress;
        float x = (float)Math.cos(age * 0.6F + (float) Math.PI);
        this.leftFrontLeg.y = 2.0F * angryProgress + 14.0F * p;
        this.leftFrontLeg.z = -6.0F * angryProgress - 10.0F * p;
        this.rightFrontLeg.y = this.leftFrontLeg.y;
        this.rightFrontLeg.z = this.leftFrontLeg.z;
        float up = u * p;
        float y = ((float) (-Math.PI / 3) + x) * angryProgress + up;
        float z = ((float) (-Math.PI / 3) - x) * angryProgress - up;
        float t5LimbDistanceP = t * 0.5F * limbDistance * p;
        this.leftHindLeg.xRot = w - t5LimbDistanceP;
        this.rightHindLeg.xRot = w + t5LimbDistanceP;
        this.leftFrontLeg.xRot = y;
        this.rightFrontLeg.xRot = z;
        this.tail.xRot = (float) (Math.PI / 6) + limbDistance * 0.75F;
        this.tail.y = -5.0F + limbDistance;
        this.tail.z = 2.0F + limbDistance * 2.0F;
        this.tail.yRot = bl ? (float)Math.sin(age * 0.7F + (float) (Math.PI / 2)) : 0.0F;
        this.rightHindBabyLeg.y = this.rightHindLeg.y;
        this.rightHindBabyLeg.z = this.rightHindLeg.z;
        this.rightHindBabyLeg.xRot = this.rightHindLeg.xRot;
        this.leftHindBabyLeg.y = this.leftHindLeg.y;
        this.leftHindBabyLeg.z = this.leftHindLeg.z;
        this.leftHindBabyLeg.xRot = this.leftHindLeg.xRot;
        this.rightFrontBabyLeg.y = this.rightFrontLeg.y;
        this.rightFrontBabyLeg.z = this.rightFrontLeg.z;
        this.rightFrontBabyLeg.xRot = this.rightFrontLeg.xRot;
        this.leftFrontBabyLeg.y = this.leftFrontLeg.y;
        this.leftFrontBabyLeg.z = this.leftFrontLeg.z;
        this.leftFrontBabyLeg.xRot = this.leftFrontLeg.xRot;
        boolean bl2 = abstractHorseEntity.isBaby();
        this.rightHindLeg.visible = !bl2;
        this.leftHindLeg.visible = !bl2;
        this.rightFrontLeg.visible = !bl2;
        this.leftFrontLeg.visible = !bl2;
        this.rightHindBabyLeg.visible = bl2;
        this.leftHindBabyLeg.visible = bl2;
        this.rightFrontBabyLeg.visible = bl2;
        this.leftFrontBabyLeg.visible = bl2;
        this.body.y = bl2 ? 10.8F : 0.0F;
    }
}

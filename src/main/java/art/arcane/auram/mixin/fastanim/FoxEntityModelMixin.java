// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.FoxModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.animal.Fox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoxModel.class)
public abstract class FoxEntityModelMixin<T extends Fox> extends AgeableListModel<T> {
    @Final
    @Shadow
    public ModelPart head;
    @Final
    @Shadow
    private ModelPart body;
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
    private ModelPart tail;
    @Shadow
    private float legMotionPos;

    @Inject(at = @At("HEAD"), method = "prepareMobModel(Lnet/minecraft/world/entity/animal/Fox;FFF)V", cancellable = true)
    public void auram$animateModel(T foxEntity, float f, float g, float h, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        boolean sleeping = foxEntity.isSleeping();
        boolean sitting = foxEntity.isSitting();
        float tailPitch = -0.05235988F;
        float headPivotX = -1.0F;
        float headPivotY = 16.5F;
        float headPivotZ = -3.0F;
        float headYaw = 0.0F;
        this.rightHindLeg.visible = !sleeping;
        this.leftHindLeg.visible = !sleeping;
        this.rightFrontLeg.visible = !sleeping;
        this.leftFrontLeg.visible = !sleeping;
        float bodyPitch = (float) (Math.PI / 2);
        float bodyPivotY = 16.0F;
        float bodyPivotZ = -6.0F;
        float bodyRoll = 0.0F;
        float rightPivotY = 17.5F;
        float rightPivotZ = 7.0F;
        float leftPivotY = 17.5F;
        float leftPivotZ = 7.0F;
        if (foxEntity.isCrouching()) {
            bodyPitch = 1.6755161F;
            float i = foxEntity.getCrouchAmount(h);
            headPivotY = 16.5F + i;
            bodyPivotY = 16.0F + i;
        } else if (sleeping) {
            bodyRoll = (float) (-Math.PI / 2);
            tailPitch = (float) (-Math.PI * 5.0 / 6.0);
            bodyPivotY = 21.0F;
            if (this.young) {
                tailPitch = -2.1816616F;
                bodyPivotZ = -2.0F;
            }

            headPivotX = 1.0F;
            headPivotY = 19.49F;
            this.head.xRot = 0.0F;
            headYaw = (float) (-Math.PI * 2.0 / 3.0);
            this.head.zRot = 0.0F;
        } else if (sitting) {
            bodyPitch = (float) (Math.PI / 6);
            bodyPivotY = 9.0F;
            bodyPivotZ = -3.0F;
            tailPitch = (float) (Math.PI / 4);
            this.tail.setPos(-4.0F, 15.0F, -2.0F);
            this.head.xRot = 0.0F;
            if (this.young) {
                headPivotY = 13.0F;
                headPivotZ = -3.75F;
            } else {
                headPivotY = 10.0F;
                headPivotZ = -0.25F;
            }

            rightPivotY = 21.5F;
            rightPivotZ = 6.75F;
            leftPivotY = 21.5F;
            leftPivotZ = 6.75F;
        }

        if (sitting) {
            this.rightHindLeg.xRot = (float) (-Math.PI * 5.0 / 12.0);
            this.leftHindLeg.xRot = (float) (-Math.PI * 5.0 / 12.0);
            this.rightFrontLeg.xRot = (float) (-Math.PI / 12);
            this.leftFrontLeg.xRot = (float) (-Math.PI / 12);
        } else if (!sleeping && !foxEntity.isFaceplanted()) {
            float fastLimbAngle = f * 0.6662F;
            float fastLimbDistance = 1.4F * g;
            float firstAngle = (float)Math.sin(fastLimbAngle + (float) (Math.PI / 2)) * fastLimbDistance;
            float secondAngle = (float)Math.sin(fastLimbAngle + (float) Math.PI + (float) (Math.PI / 2)) * fastLimbDistance;
            this.rightHindLeg.xRot = firstAngle;
            this.leftHindLeg.xRot = secondAngle;
            this.rightFrontLeg.xRot = secondAngle;
            this.leftFrontLeg.xRot = firstAngle;
        }

        this.body.xRot = bodyPitch;
        this.body.zRot = bodyRoll;
        this.body.setPos(0.0F, bodyPivotY, bodyPivotZ);
        this.tail.xRot = tailPitch;
        this.head.setPos(headPivotX, headPivotY, headPivotZ);
        this.head.yRot = headYaw;
        this.head.zRot = sleeping ? 0.0F : foxEntity.getHeadRollAngle(h);
        this.rightHindLeg.setPos(-5.0F, rightPivotY, rightPivotZ);
        this.leftHindLeg.setPos(-1.0F, leftPivotY, leftPivotZ);
    }

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/animal/Fox;FFFFF)V", cancellable = true)
    public void auram$setAngles(T foxEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        boolean sleeping = foxEntity.isSleeping();
        boolean sneaking = foxEntity.isCrouching();
        boolean walking = foxEntity.isFaceplanted();
        if (!sleeping && !walking && !sneaking) {
            this.head.xRot = j * (float) (Math.PI / 180.0);
            this.head.yRot = i * (float) (Math.PI / 180.0);
        }

        if (sleeping) {
            this.head.xRot = 0.0F;
            this.head.yRot = (float) (-Math.PI * 2.0 / 3.0);
            this.head.zRot = (float)Math.sin(h * 0.027F + (float) (Math.PI / 2)) * 0.04555F;
        }

        if (sneaking) {
            float k = (float)Math.sin(h + (float) (Math.PI / 2)) * 0.01F;
            float k2 = k * 0.5F;
            this.body.yRot = k;
            this.rightHindLeg.zRot = k;
            this.leftHindLeg.zRot = k;
            this.rightFrontLeg.zRot = k2;
            this.leftFrontLeg.zRot = k2;
        }

        if (walking) {
            this.legMotionPos += 0.67F;
            float fastPitch = this.legMotionPos * 0.4662F;
            float pitchOne = (float)Math.sin(fastPitch + (float) (Math.PI / 2)) * 0.1F;
            float pitchTwo = (float)Math.sin(fastPitch + (float) Math.PI + (float) (Math.PI / 2)) * 0.1F;
            this.rightHindLeg.xRot = pitchOne;
            this.leftHindLeg.xRot = pitchTwo;
            this.rightFrontLeg.xRot = pitchTwo;
            this.leftFrontLeg.xRot = pitchOne;
        }
    }
}

// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.animal.Wolf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WolfModel.class, priority = 900)
public class WolfEntityModelMixin<T extends Wolf> {
    @Final
    @Shadow
    private ModelPart head;
    @Final
    @Shadow
    private ModelPart realHead;
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
    @Final
    @Shadow
    private ModelPart realTail;
    @Final
    @Shadow
    private ModelPart upperBody;

    @Inject(at = @At("HEAD"), method = "prepareMobModel(Lnet/minecraft/world/entity/animal/Wolf;FFF)V", cancellable = true)
    public void auram$animateModel(T wolfEntity, float f, float g, float h, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float angleOne = 0.0F;
        float angleTwo = 0.0F;
        boolean hasAnger = wolfEntity.isAngry();
        if (!hasAnger) {
            float fastAngle = f * 0.6662F;
            float fastDistance = 1.4F * g;
            angleOne = (float)Math.sin(fastAngle + (float) (Math.PI / 2)) * fastDistance;
            angleTwo = (float)Math.sin(fastAngle + (float) Math.PI + (float) (Math.PI / 2)) * fastDistance;
        }

        this.tail.yRot = angleOne;
        if (wolfEntity.isInSittingPose()) {
            this.upperBody.setPos(-1.0F, 16.0F, -3.0F);
            this.upperBody.xRot = (float) (Math.PI * 2.0 / 5.0);
            this.upperBody.yRot = 0.0F;
            this.body.setPos(0.0F, 18.0F, 0.0F);
            this.body.xRot = (float) (Math.PI / 4);
            this.tail.setPos(-1.0F, 21.0F, 6.0F);
            this.rightHindLeg.setPos(-2.5F, 22.7F, 2.0F);
            this.rightHindLeg.xRot = (float) (Math.PI * 3.0 / 2.0);
            this.leftHindLeg.setPos(0.5F, 22.7F, 2.0F);
            this.leftHindLeg.xRot = (float) (Math.PI * 3.0 / 2.0);
            this.rightFrontLeg.xRot = 5.811947F;
            this.rightFrontLeg.setPos(-2.49F, 17.0F, -4.0F);
            this.leftFrontLeg.xRot = 5.811947F;
            this.leftFrontLeg.setPos(0.51F, 17.0F, -4.0F);
        } else {
            this.body.setPos(0.0F, 14.0F, 2.0F);
            this.body.xRot = (float) (Math.PI / 2);
            this.upperBody.setPos(-1.0F, 14.0F, -3.0F);
            this.upperBody.xRot = this.body.xRot;
            this.tail.setPos(-1.0F, 12.0F, 8.0F);
            this.rightHindLeg.setPos(-2.5F, 16.0F, 7.0F);
            this.leftHindLeg.setPos(0.5F, 16.0F, 7.0F);
            this.rightFrontLeg.setPos(-2.5F, 16.0F, -4.0F);
            this.leftFrontLeg.setPos(0.5F, 16.0F, -4.0F);
            if (hasAnger) {
                float fastAngle = f * 0.6662F;
                float fastDistance = 1.4F * g;
                angleOne = (float)Math.sin(fastAngle + (float) (Math.PI / 2)) * fastDistance;
                angleTwo = (float)Math.sin(fastAngle + (float) Math.PI + (float) (Math.PI / 2)) * fastDistance;
            }

            this.rightHindLeg.xRot = angleOne;
            this.leftHindLeg.xRot = angleTwo;
            this.rightFrontLeg.xRot = angleTwo;
            this.leftFrontLeg.xRot = angleOne;
        }

        this.realHead.zRot = wolfEntity.getHeadRollAngle(h) + wolfEntity.getBodyRollAngle(h, 0.0F);
        this.upperBody.zRot = wolfEntity.getBodyRollAngle(h, -0.08F);
        this.body.zRot = wolfEntity.getBodyRollAngle(h, -0.16F);
        this.realTail.zRot = wolfEntity.getBodyRollAngle(h, -0.2F);
    }
}

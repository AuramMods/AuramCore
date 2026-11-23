// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.TurtleModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.animal.Turtle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TurtleModel.class, priority = 900)
public abstract class TurtleEntityModelMixin<T extends Turtle> extends QuadrupedModel<T> {
    @Final
    @Shadow
    private ModelPart eggBelly;

    protected TurtleEntityModelMixin(ModelPart modelPart, boolean bl, float f, float g, float h, float i, int j) {
        super(modelPart, bl, f, g, h, i, j);
    }

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/animal/Turtle;FFFFF)V", cancellable = true)
    public void auram$setAngles(T turtleEntity, float limbAngle, float limbDistance, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        this.head.xRot = j * (float) (Math.PI / 180.0);
        this.head.yRot = i * (float) (Math.PI / 180.0);
        if (!turtleEntity.isInWater() && turtleEntity.onGround()) {
            boolean digging = turtleEntity.isLayingEgg();
            float k = digging ? 4.0F : 1.0F;
            float l = digging ? 2.0F : 1.0F;
            float limb5 = limbAngle * 5.0F;
            float kLimb5 = k * limb5;
            float eightLimbL = 8.0F * limbDistance * l;
            float threeLimb = 3.0F * limbDistance;
            this.rightHindLeg.xRot = 0.0F;
            this.leftHindLeg.xRot = 0.0F;
            this.rightFrontLeg.zRot = 0.0F;
            this.leftFrontLeg.zRot = 0.0F;
            this.rightFrontLeg.yRot = (float)Math.sin(kLimb5 + (float) Math.PI + (float) (Math.PI / 2)) * eightLimbL;
            this.leftFrontLeg.yRot = (float)Math.sin(kLimb5 + (float) (Math.PI / 2)) * eightLimbL;
            this.rightHindLeg.yRot = (float)Math.sin(limb5 + (float) Math.PI + (float) (Math.PI / 2)) * threeLimb;
            this.leftHindLeg.yRot = (float)Math.sin(limb5 + (float) (Math.PI / 2)) * threeLimb;
        } else {
            float fastLimbAngle = limbAngle * 0.39972F;
            float fastLimbDistance = 0.5F * limbDistance;
            float firstAngle = (float)Math.sin(fastLimbAngle + (float) (Math.PI / 2)) * fastLimbDistance;
            float secondAngle = (float)Math.sin(fastLimbAngle + (float) Math.PI + (float) (Math.PI / 2)) * fastLimbDistance;
            this.rightHindLeg.xRot = firstAngle;
            this.leftHindLeg.xRot = secondAngle;
            this.rightFrontLeg.zRot = secondAngle;
            this.leftFrontLeg.zRot = firstAngle;
            this.rightFrontLeg.yRot = 0.0F;
            this.leftFrontLeg.yRot = 0.0F;
            this.rightHindLeg.yRot = 0.0F;
            this.leftHindLeg.yRot = 0.0F;
        }

        this.eggBelly.visible = !this.young && turtleEntity.hasEgg();
    }
}

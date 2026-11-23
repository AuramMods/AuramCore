// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.PolarBearModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.animal.PolarBear;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PolarBearModel.class, priority = 900)
public abstract class PolarBearEntityModelMixin<T extends PolarBear> extends QuadrupedModel<T> {
    protected PolarBearEntityModelMixin(ModelPart modelPart, boolean bl, float f, float g, float h, float i, int j) {
        super(modelPart, bl, f, g, h, i, j);
    }

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/animal/PolarBear;FFFFF)V", cancellable = true)
    public void auram$setAngles(T polarBearEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        this.head.xRot = j * (float) (Math.PI / 180.0);
        this.head.yRot = i * (float) (Math.PI / 180.0);
        float fastLimbAngle = f * 0.6662F;
        float fastLimbDistance = 1.4F * g;
        float firstAngle = (float)Math.sin(fastLimbAngle + (float) (Math.PI / 2)) * fastLimbDistance;
        float secondAngle = (float)Math.sin(fastLimbAngle + (float) Math.PI + (float) (Math.PI / 2)) * fastLimbDistance;
        this.rightHindLeg.xRot = firstAngle;
        this.leftHindLeg.xRot = secondAngle;
        this.rightFrontLeg.xRot = secondAngle;
        this.leftFrontLeg.xRot = firstAngle;
        float k = h - polarBearEntity.tickCount;
        float l = polarBearEntity.getStandingAnimationScale(k);
        l *= l;
        float m = 1.0F - l;
        this.body.xRot = (float) (Math.PI / 2) - l * 1.0995574F;
        this.body.y = 9.0F * m + 11.0F * l;
        float pivotY = 14.0F * m - 6.0F * l;
        float pivotZ = -8.0F * m - 4.0F * l;
        this.rightFrontLeg.y = 14.0F * m - 6.0F * l;
        this.rightFrontLeg.z = -8.0F * m - 4.0F * l;
        float pitches = l * 1.4137167F;
        this.rightFrontLeg.xRot -= pitches;
        this.leftFrontLeg.y = pivotY;
        this.leftFrontLeg.z = pivotZ;
        this.leftFrontLeg.xRot -= pitches;
        if (this.young) {
            this.head.y = 10.0F * m - 9.0F * l;
            this.head.z = -16.0F * m - 7.0F * l;
        } else {
            this.head.y = 10.0F * m - 14.0F * l;
            this.head.z = -16.0F * m - 3.0F * l;
        }

        this.head.xRot += l * 0.4712389F;
    }
}

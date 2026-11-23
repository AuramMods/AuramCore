// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.ModelUtils;
import net.minecraft.client.model.PandaModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.animal.Panda;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PandaModel.class, priority = 900)
public abstract class PandaEntityModelMixin<T extends Panda> extends QuadrupedModel<T> {
    @Shadow
    private float sitAmount;
    @Shadow
    private float lieOnBackAmount;
    @Shadow
    private float rollAmount;

    protected PandaEntityModelMixin(ModelPart modelPart, boolean bl, float f, float g, float h, float i, int j) {
        super(modelPart, bl, f, g, h, i, j);
    }

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/animal/Panda;FFFFF)V", cancellable = true)
    public void auram$setAngles(T pandaEntity, float limbAngle, float limbDistance, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        this.head.xRot = j * (float) (Math.PI / 180.0);
        this.head.yRot = i * (float) (Math.PI / 180.0);
        float fastLimbAngle = limbAngle * 0.6662F;
        float fastLimbDistance = 1.4F * limbDistance;
        float firstAngle = (float)Math.sin(fastLimbAngle + (float) (Math.PI / 2)) * fastLimbDistance;
        float secondAngle = (float)Math.sin(fastLimbAngle + (float) Math.PI + (float) (Math.PI / 2)) * fastLimbDistance;
        this.rightHindLeg.xRot = firstAngle;
        this.leftHindLeg.xRot = secondAngle;
        this.rightFrontLeg.xRot = secondAngle;
        this.leftFrontLeg.xRot = firstAngle;
        boolean askingForBamboo = pandaEntity.getUnhappyCounter() > 0;
        boolean sneezing = pandaEntity.isSneezing();
        int sneezeProgress = pandaEntity.getSneezeCounter();
        boolean eating = pandaEntity.isEating();
        boolean scared = pandaEntity.isScared();
        if (askingForBamboo) {
            float headYawRoll = 0.35F * (float)Math.sin(0.6F * h);
            this.head.yRot = headYawRoll;
            this.head.zRot = headYawRoll;
            float frontLegPitches = 0.75F * (float)Math.sin(0.3F * h);
            this.rightFrontLeg.xRot = -frontLegPitches;
            this.leftFrontLeg.xRot = frontLegPitches;
        } else {
            this.head.zRot = 0.0F;
        }

        if (sneezing) {
            if (sneezeProgress < 15) {
                this.head.xRot = (float) (-Math.PI / 4) * sneezeProgress * 0.071433F;
            } else if (sneezeProgress < 20) {
                float l = (sneezeProgress - 15) * 0.2F;
                this.head.xRot = (float) (-Math.PI / 4) + (float) (Math.PI / 4) * l;
            }
        }

        if (this.sitAmount > 0.0F) {
            this.body.xRot = ModelUtils.rotlerpRad(this.body.xRot, 1.7407963F, this.sitAmount);
            this.head.xRot = ModelUtils.rotlerpRad(this.head.xRot, (float) (Math.PI / 2), this.sitAmount);
            this.rightFrontLeg.zRot = -0.27079642F;
            this.leftFrontLeg.zRot = 0.27079642F;
            this.rightHindLeg.zRot = 0.5707964F;
            this.leftHindLeg.zRot = -0.5707964F;
            if (eating) {
                float sinTwo = 0.2F * (float)Math.sin(h * 0.6F);
                this.head.xRot = (float) (Math.PI / 2) + sinTwo;
                float legPitches = -0.4F - sinTwo;
                this.rightFrontLeg.xRot = legPitches;
                this.leftFrontLeg.xRot = legPitches;
            }

            if (scared) {
                this.head.xRot = 2.1707964F;
                this.rightFrontLeg.xRot = -0.9F;
                this.leftFrontLeg.xRot = -0.9F;
            }
        } else {
            this.rightHindLeg.zRot = 0.0F;
            this.leftHindLeg.zRot = 0.0F;
            this.rightFrontLeg.zRot = 0.0F;
            this.leftFrontLeg.zRot = 0.0F;
        }

        if (this.lieOnBackAmount > 0.0F) {
            float hind = 0.6F * (float)Math.sin(h * 0.15F);
            this.rightHindLeg.xRot = -hind;
            this.leftHindLeg.xRot = hind;
            float front = 0.3F * (float)Math.sin(h * 0.25F);
            this.rightFrontLeg.xRot = front;
            this.leftFrontLeg.xRot = -front;
            this.head.xRot = ModelUtils.rotlerpRad(this.head.xRot, (float) (Math.PI / 2), this.lieOnBackAmount);
        }

        if (this.rollAmount > 0.0F) {
            this.head.xRot = ModelUtils.rotlerpRad(this.head.xRot, 2.0561945F, this.rollAmount);
            float legs = 0.5F * (float)Math.sin(h * 0.5F);
            this.rightHindLeg.xRot = -legs;
            this.leftHindLeg.xRot = legs;
            this.rightFrontLeg.xRot = legs;
            this.leftFrontLeg.xRot = -legs;
        }
    }
}

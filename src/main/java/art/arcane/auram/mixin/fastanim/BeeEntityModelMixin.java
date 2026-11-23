// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.BeeModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeeModel.class)
public class BeeEntityModelMixin<T extends Bee> {
    @Final
    @Shadow
    private ModelPart bone;
    @Final
    @Shadow
    private ModelPart rightWing;
    @Final
    @Shadow
    private ModelPart leftWing;
    @Final
    @Shadow
    private ModelPart frontLeg;
    @Final
    @Shadow
    private ModelPart midLeg;
    @Final
    @Shadow
    private ModelPart backLeg;
    @Final
    @Shadow
    private ModelPart stinger;
    @Final
    @Shadow
    private ModelPart leftAntenna;
    @Final
    @Shadow
    private ModelPart rightAntenna;

    @Inject(at = @At("HEAD"), method = "prepareMobModel(Lnet/minecraft/world/entity/animal/Bee;FFF)V", cancellable = true)
    public void auram$animateModel(T beeEntity, float f, float g, float h, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
    }

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/animal/Bee;FFFFF)V", cancellable = true)
    public void auram$setAngles(T beeEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float bodyPitch = beeEntity.getRollAmount(h);
        this.stinger.visible = !beeEntity.hasStung();
        this.leftAntenna.xRot = 0.0F;
        this.rightAntenna.xRot = 0.0F;
        this.bone.xRot = 0.0F;
        boolean bl = beeEntity.onGround() && beeEntity.getDeltaMovement().lengthSqr() < 1.0E-7;
        if (bl) {
            this.rightWing.yRot = -0.2618F;
            this.rightWing.zRot = 0.0F;
            this.leftWing.yRot = 0.2618F;
            this.leftWing.zRot = 0.0F;
            this.frontLeg.xRot = 0.0F;
            this.midLeg.xRot = 0.0F;
            this.backLeg.xRot = 0.0F;
        } else {
            this.rightWing.yRot = 0.0F;
            float roll = (float)Math.sin(h * 2.1F + (float) (Math.PI / 2)) * 0.4712389F;
            this.rightWing.zRot = roll;
            this.leftWing.yRot = 0.0F;
            this.leftWing.zRot = -roll;
            this.frontLeg.xRot = (float) (Math.PI / 4);
            this.midLeg.xRot = (float) (Math.PI / 4);
            this.backLeg.xRot = (float) (Math.PI / 4);
            this.bone.xRot = 0.0F;
            this.bone.yRot = 0.0F;
            this.bone.zRot = 0.0F;
        }

        if (!beeEntity.isAngry()) {
            this.bone.xRot = 0.0F;
            this.bone.yRot = 0.0F;
            this.bone.zRot = 0.0F;
            if (!bl) {
                float k = (float)Math.sin(h * 0.18F + (float) (Math.PI / 2));
                float k3 = k * 0.09424778F;
                this.bone.xRot = 0.1F + k * 0.07853982F;
                this.leftAntenna.xRot = k3;
                this.rightAntenna.xRot = k3;
                this.frontLeg.xRot = -k * (float) (Math.PI / 10) + (float) (Math.PI / 8);
                this.backLeg.xRot = -k * (float) (Math.PI / 20) + (float) (Math.PI / 4);
                this.bone.y = 19.0F - k * 0.9F;
            }
        }

        if (bodyPitch > 0.0F) {
            float fl = (bodyPitch - 3.0915928F) % (float) (Math.PI * 2);
            if (fl < (float) -Math.PI) {
                fl += (float) (Math.PI * 2);
            }

            if (fl >= (float) Math.PI) {
                fl -= (float) (Math.PI * 2);
            }

            this.bone.xRot = 3.0915928F + this.bone.xRot * fl;
        }
    }
}

// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.ParrotModel;
import net.minecraft.client.model.ParrotModel.State;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.animal.Parrot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ParrotModel.class, priority = 900)
public class ParrotEntityModelMixin<T extends Parrot> {
    @Final
    @Shadow
    private ModelPart root;
    @Final
    @Shadow
    private ModelPart body;
    @Final
    @Shadow
    private ModelPart tail;
    @Final
    @Shadow
    private ModelPart leftWing;
    @Final
    @Shadow
    private ModelPart rightWing;
    @Final
    @Shadow
    private ModelPart head;
    @Final
    @Shadow
    private ModelPart feather;
    @Final
    @Shadow
    private ModelPart leftLeg;
    @Final
    @Shadow
    private ModelPart rightLeg;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/client/model/ParrotModel$State;IFFFFF)V", cancellable = true)
    private void auram$setAngles(State pose, int i, float f, float g, float h, float j, float k, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        this.head.xRot = k * (float) (Math.PI / 180.0);
        this.head.yRot = j * (float) (Math.PI / 180.0);
        this.head.zRot = 0.0F;
        this.head.x = 0.0F;
        this.body.x = 0.0F;
        this.tail.x = 0.0F;
        this.rightWing.x = -1.5F;
        this.leftWing.x = 1.5F;
        switch (pose) {
            case SITTING:
                break;
            case PARTY:
                float l = (float)Math.sin(i + (float) (Math.PI / 2));
                float m = (float)Math.sin(i);
                this.head.x = l;
                this.head.y = 15.69F + m;
                this.head.xRot = 0.0F;
                this.head.yRot = 0.0F;
                this.head.zRot = m * 0.4F;
                this.body.x = l;
                this.body.y = 16.5F + m;
                this.leftWing.zRot = -0.0873F - h;
                this.leftWing.x = 1.5F + l;
                this.leftWing.y = 16.94F + m;
                this.rightWing.zRot = 0.0873F + h;
                this.rightWing.x = -1.5F + l;
                this.rightWing.y = 16.94F + m;
                this.tail.x = l;
                this.tail.y = 21.07F + m;
                break;
            case STANDING:
                float oneFourG = 1.4F * g;
                float fastAngle = f * 0.6662F;
                this.leftLeg.xRot = this.leftLeg.xRot + (float)Math.sin(fastAngle + (float) (Math.PI / 2)) * oneFourG;
                this.rightLeg.xRot = this.rightLeg.xRot + (float)Math.sin(fastAngle + (float) Math.PI + (float) (Math.PI / 2)) * oneFourG;
            default:
                float n = h * 0.3F;
                this.head.y = 15.69F + n;
                this.tail.xRot = 1.015F + (float)Math.sin(f * 0.6662F + (float) (Math.PI / 2)) * 0.3F * g;
                this.tail.y = 21.07F + n;
                this.body.y = 16.5F + n;
                this.leftWing.zRot = -0.0873F - h;
                this.leftWing.y = 16.94F + n;
                this.rightWing.zRot = 0.0873F + h;
                this.rightWing.y = 16.94F + n;
                this.leftLeg.y = 22.0F + n;
                this.rightLeg.y = 22.0F + n;
        }
    }

    @Inject(at = @At("HEAD"), method = "prepare(Lnet/minecraft/client/model/ParrotModel$State;)V", cancellable = true)
    private void auram$animateModel(State pose, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        this.feather.xRot = -0.2214F;
        this.body.xRot = 0.4937F;
        this.leftWing.xRot = -0.6981F;
        this.leftWing.yRot = (float) -Math.PI;
        this.rightWing.xRot = -0.6981F;
        this.rightWing.yRot = (float) -Math.PI;
        this.leftLeg.xRot = -0.0299F;
        this.rightLeg.xRot = -0.0299F;
        this.leftLeg.y = 22.0F;
        this.rightLeg.y = 22.0F;
        this.leftLeg.zRot = 0.0F;
        this.rightLeg.zRot = 0.0F;
        switch (pose) {
            case SITTING:
                float f = 1.9F;
                this.head.y = 17.59F;
                this.tail.xRot = 1.5388988F;
                this.tail.y = 22.97F;
                this.body.y = 18.4F;
                this.leftWing.zRot = -0.0873F;
                this.leftWing.y = 18.84F;
                this.rightWing.zRot = 0.0873F;
                this.rightWing.y = 18.84F;
                this.leftLeg.y += 1.9F;
                this.rightLeg.y += 1.9F;
                this.leftLeg.xRot += (float) (Math.PI / 2);
                this.rightLeg.xRot += (float) (Math.PI / 2);
                break;
            case PARTY:
                this.leftLeg.zRot = (float) (-Math.PI / 9);
                this.rightLeg.zRot = (float) (Math.PI / 9);
            case STANDING:
            default:
                break;
            case FLYING:
                this.leftLeg.xRot += (float) (Math.PI * 2.0 / 9.0);
                this.rightLeg.xRot += (float) (Math.PI * 2.0 / 9.0);
        }
    }
}

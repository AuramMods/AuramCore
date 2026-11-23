// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.RabbitModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.animal.Rabbit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RabbitModel.class, priority = 900)
public class RabbitEntityModelMixin<T extends Rabbit> {
    @Final
    @Shadow
    private ModelPart leftRearFoot;
    @Final
    @Shadow
    private ModelPart rightRearFoot;
    @Final
    @Shadow
    private ModelPart leftHaunch;
    @Final
    @Shadow
    private ModelPart rightHaunch;
    @Final
    @Shadow
    private ModelPart body;
    @Final
    @Shadow
    private ModelPart leftFrontLeg;
    @Final
    @Shadow
    private ModelPart rightFrontLeg;
    @Final
    @Shadow
    private ModelPart head;
    @Final
    @Shadow
    private ModelPart rightEar;
    @Final
    @Shadow
    private ModelPart leftEar;
    @Final
    @Shadow
    private ModelPart tail;
    @Final
    @Shadow
    private ModelPart nose;
    @Shadow
    private float jumpRotation;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/animal/Rabbit;FFFFF)V", cancellable = true)
    public void auram$setAngles(T rabbitEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float k = h - rabbitEntity.tickCount;
        float jPi = j * (float) (Math.PI / 180.0);
        this.nose.xRot = jPi;
        this.head.xRot = jPi;
        this.rightEar.xRot = jPi;
        this.leftEar.xRot = jPi;
        float iPi = i * (float) (Math.PI / 180.0);
        this.nose.yRot = iPi;
        this.head.yRot = iPi;
        this.rightEar.yRot = this.nose.yRot - (float) (Math.PI / 12);
        this.leftEar.yRot = this.nose.yRot + (float) (Math.PI / 12);
        float jumpProgress = (float)Math.sin(rabbitEntity.getJumpCompletion(k) * (float) Math.PI);
        float jump50 = jumpProgress * 50.0F;
        float jump5021 = (jump50 - 21.0F) * (float) (Math.PI / 180.0);
        this.leftHaunch.xRot = jump5021;
        this.rightHaunch.xRot = jump5021;
        float jump50Pi180 = jump50 * (float) (Math.PI / 180.0);
        this.leftRearFoot.xRot = jump50Pi180;
        this.rightRearFoot.xRot = jump50Pi180;
        float jump40 = (jumpProgress * -40.0F - 11.0F) * (float) (Math.PI / 180.0);
        this.leftFrontLeg.xRot = jump40;
        this.rightFrontLeg.xRot = jump40;
        this.jumpRotation = jumpProgress;
    }

    @Inject(at = @At("HEAD"), method = "prepareMobModel(Lnet/minecraft/world/entity/animal/Rabbit;FFF)V", cancellable = true)
    public void auram$animateModel(T rabbitEntity, float f, float g, float h, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
    }
}

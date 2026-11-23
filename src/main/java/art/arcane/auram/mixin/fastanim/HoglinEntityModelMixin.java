// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.HoglinModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.hoglin.HoglinBase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HoglinModel.class)
public class HoglinEntityModelMixin<T extends Mob & HoglinBase> {
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
    private ModelPart rightFrontLeg;
    @Final
    @Shadow
    private ModelPart leftFrontLeg;
    @Final
    @Shadow
    private ModelPart rightHindLeg;
    @Final
    @Shadow
    private ModelPart leftHindLeg;
    @Final
    @Shadow
    private ModelPart mane;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/Mob;FFFFF)V", cancellable = true)
    public void auram$setAngles(T mobEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float distanceBySinAngle = g * (float)Math.sin(f);
        this.rightEar.zRot = (float) (-Math.PI * 2.0 / 9.0) - distanceBySinAngle;
        this.leftEar.zRot = (float) (Math.PI * 2.0 / 9.0) + distanceBySinAngle;
        this.head.yRot = i * (float) (Math.PI / 180.0);
        float l = 1.0F - Math.abs(10 - 2 * mobEntity.getAttackAnimationRemainingTicks()) * 0.1F;
        this.head.xRot = 0.87266463F + l * -1.2217305F;
        if (mobEntity.isBaby()) {
            this.head.y = 2.0F + l * 3.0F;
            this.mane.z = -3.0F;
        } else {
            this.head.y = 2.0F;
            this.mane.z = -7.0F;
        }

        float onePointTwoLimb = 1.2F * g;
        float firstAngle = (float)Math.sin(f + (float) (Math.PI / 2)) * onePointTwoLimb;
        float secondAngle = (float)Math.sin(f + (float) Math.PI + (float) (Math.PI / 2)) * onePointTwoLimb;
        this.rightFrontLeg.xRot = firstAngle;
        this.leftHindLeg.xRot = firstAngle;
        this.rightHindLeg.xRot = secondAngle;
        this.leftFrontLeg.xRot = secondAngle;
    }
}

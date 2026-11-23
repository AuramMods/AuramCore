// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SpiderModel.class, priority = 900)
public class SpiderEntityModelMixin<T extends Entity> {
    @Final
    @Shadow
    private ModelPart root;
    @Final
    @Shadow
    private ModelPart head;
    @Final
    @Shadow
    private ModelPart rightHindLeg;
    @Final
    @Shadow
    private ModelPart leftHindLeg;
    @Final
    @Shadow
    private ModelPart rightMiddleHindLeg;
    @Final
    @Shadow
    private ModelPart leftMiddleHindLeg;
    @Final
    @Shadow
    private ModelPart rightMiddleFrontLeg;
    @Final
    @Shadow
    private ModelPart leftMiddleFrontLeg;
    @Final
    @Shadow
    private ModelPart rightFrontLeg;
    @Final
    @Shadow
    private ModelPart leftFrontLeg;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", cancellable = true)
    public void auram$setAngles(T entity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        this.head.yRot = i * (float) (Math.PI / 180.0);
        this.head.xRot = j * (float) (Math.PI / 180.0);
        float fastAngle = f * 0.6662F;
        float fastAngleTwo = fastAngle * 2.0F;
        float hindYaw = (float) (Math.PI / 4) - (float)Math.sin(fastAngleTwo + (float) (Math.PI / 2)) * 0.4F * g;
        float middleYaw = (float) (Math.PI / 8) - (float)Math.sin(fastAngleTwo + (float) Math.PI + (float) (Math.PI / 2)) * 0.4F * g;
        float middleFrontYaw = (float) (-Math.PI / 8) - (float)Math.sin(fastAngleTwo + (float) (Math.PI / 2) + (float) (Math.PI / 2)) * 0.4F * g;
        float frontYaw = (float) (-Math.PI / 4) - (float)Math.sin(fastAngleTwo + (float) (Math.PI * 3.0 / 2.0) + (float) (Math.PI / 2)) * 0.4F * g;
        this.rightHindLeg.yRot = hindYaw;
        this.leftHindLeg.yRot = -hindYaw;
        this.rightMiddleHindLeg.yRot = middleYaw;
        this.leftMiddleHindLeg.yRot = -middleYaw;
        this.rightMiddleFrontLeg.yRot = middleFrontYaw;
        this.leftMiddleFrontLeg.yRot = -middleFrontYaw;
        this.rightFrontLeg.yRot = frontYaw;
        this.leftFrontLeg.yRot = -frontYaw;
        float hindRoll = (float) (-Math.PI / 4) + Math.abs((float)Math.sin(fastAngle) * 0.4F) * g;
        float middleRoll = -0.58119464F + Math.abs((float)Math.sin(fastAngle + (float) Math.PI) * 0.4F) * g;
        float middleFrontRoll = -0.58119464F + Math.abs((float)Math.sin(fastAngle + (float) (Math.PI / 2)) * 0.4F) * g;
        float frontRoll = (float) (-Math.PI / 4) + Math.abs((float)Math.sin(fastAngle + (float) (Math.PI * 3.0 / 2.0)) * 0.4F) * g;
        this.rightHindLeg.zRot = hindRoll;
        this.leftHindLeg.zRot = -hindRoll;
        this.rightMiddleHindLeg.zRot = middleRoll;
        this.leftMiddleHindLeg.zRot = -middleRoll;
        this.rightMiddleFrontLeg.zRot = middleFrontRoll;
        this.leftMiddleFrontLeg.zRot = -middleFrontRoll;
        this.rightFrontLeg.zRot = frontRoll;
        this.leftFrontLeg.zRot = -frontRoll;
    }
}

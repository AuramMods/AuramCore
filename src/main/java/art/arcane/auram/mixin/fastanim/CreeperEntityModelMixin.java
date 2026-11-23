// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreeperModel.class)
public class CreeperEntityModelMixin<T extends Entity> {
    @Final
    @Shadow
    private ModelPart head;
    @Final
    @Shadow
    private ModelPart leftHindLeg;
    @Final
    @Shadow
    private ModelPart rightHindLeg;
    @Final
    @Shadow
    private ModelPart leftFrontLeg;
    @Final
    @Shadow
    private ModelPart rightFrontLeg;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", cancellable = true)
    public void auram$setAngles(T entity, float limbAngle, float limbDistance, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        this.head.xRot = j * (float) (Math.PI / 180.0);
        this.head.yRot = i * (float) (Math.PI / 180.0);
        float fastLimbAngle = limbAngle * 0.6662F;
        float fastLimbDistance = 1.4F * limbDistance;
        float firstAngle = (float)Math.sin(fastLimbAngle + (float) (Math.PI / 2)) * fastLimbDistance;
        float secondAngle = (float)Math.sin(fastLimbAngle + (float) Math.PI + (float) (Math.PI / 2)) * fastLimbDistance;
        this.leftHindLeg.xRot = firstAngle;
        this.rightHindLeg.xRot = secondAngle;
        this.leftFrontLeg.xRot = secondAngle;
        this.rightFrontLeg.xRot = firstAngle;
    }
}

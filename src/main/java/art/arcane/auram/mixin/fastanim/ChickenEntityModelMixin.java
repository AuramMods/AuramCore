// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.ChickenModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChickenModel.class)
public class ChickenEntityModelMixin<T extends Entity> {
    @Final
    @Shadow
    private ModelPart head;
    @Final
    @Shadow
    private ModelPart rightLeg;
    @Final
    @Shadow
    private ModelPart leftLeg;
    @Final
    @Shadow
    private ModelPart rightWing;
    @Final
    @Shadow
    private ModelPart leftWing;
    @Final
    @Shadow
    private ModelPart beak;
    @Final
    @Shadow
    private ModelPart redThing;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", cancellable = true)
    public void auram$setAngles(T entity, float limbAngle, float limbDistance, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float pitches = j * (float) (Math.PI / 180.0);
        this.head.xRot = pitches;
        this.beak.xRot = pitches;
        this.redThing.xRot = pitches;
        float yaws = i * (float) (Math.PI / 180.0);
        this.head.yRot = yaws;
        this.beak.yRot = yaws;
        this.redThing.yRot = yaws;
        float fastLimbAngle = limbAngle * 0.6662F;
        float fastLimbDistance = 1.4F * limbDistance;
        this.rightLeg.xRot = (float)Math.sin(fastLimbAngle + (float) (Math.PI / 2)) * fastLimbDistance;
        this.leftLeg.xRot = (float)Math.sin(fastLimbAngle + (float) Math.PI + (float) (Math.PI / 2)) * fastLimbDistance;
        this.rightWing.zRot = h;
        this.leftWing.zRot = -h;
    }
}

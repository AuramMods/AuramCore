// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.WardenModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WardenModel.class, priority = 900)
public class WardenEntityModelMixin<T extends Warden> {
    @Final
    @Shadow
    public ModelPart body;
    @Final
    @Shadow
    public ModelPart head;
    @Final
    @Shadow
    public ModelPart leftLeg;
    @Final
    @Shadow
    public ModelPart leftArm;
    @Final
    @Shadow
    public ModelPart rightArm;
    @Final
    @Shadow
    public ModelPart rightLeg;

    @Inject(at = @At("HEAD"), method = "animateIdlePose(F)V", cancellable = true)
    private void auram$setHeadAndBodyAngles(float f, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float g = f * 0.1F;
        float h = (float)Math.sin(g + (float) (Math.PI / 2));
        float i = (float)Math.sin(g);
        this.head.zRot += 0.06F * h;
        this.head.xRot += 0.06F * i;
        this.body.zRot += 0.025F * i;
        this.body.xRot += 0.025F * h;
    }

    @Inject(at = @At("HEAD"), method = "animateWalk(FF)V", cancellable = true)
    private void auram$setLimbAngles(float f, float g, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float h = Math.min(0.5F, 3.0F * g);
        float i = f * 0.8662F;
        float cos = (float)Math.sin(i + (float) (Math.PI / 2));
        float sin = (float)Math.sin(i);
        float l = Math.min(0.35F, h);
        float sinh = sin * h;
        float cosh = cos * h;
        this.head.zRot += 0.3F * sinh;
        this.head.xRot = this.head.xRot + 1.2F * (float)Math.sin(i + (float) (Math.PI / 2) + (float) (Math.PI / 2)) * l;
        this.body.zRot = 0.1F * sinh;
        this.body.xRot = cos * l;
        this.leftLeg.xRot = cosh;
        this.rightLeg.xRot = (float)Math.sin(i + (float) Math.PI + (float) (Math.PI / 2)) * h;
        this.leftArm.xRot = -(0.8F * cosh);
        this.leftArm.zRot = 0.0F;
        this.rightArm.xRot = -(0.8F * sinh);
        this.rightArm.zRot = 0.0F;
        this.leftArm.yRot = 0.0F;
        this.leftArm.z = 1.0F;
        this.leftArm.x = 13.0F;
        this.leftArm.y = -13.0F;
        this.rightArm.yRot = 0.0F;
        this.rightArm.z = 1.0F;
        this.rightArm.x = -13.0F;
        this.rightArm.y = -13.0F;
    }
}

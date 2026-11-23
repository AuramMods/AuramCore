// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.IronGolem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IronGolemModel.class)
public class IronGolemEntityModelMixin<T extends IronGolem> {
    @Final
    @Shadow
    private ModelPart head;
    @Final
    @Shadow
    private ModelPart rightArm;
    @Final
    @Shadow
    private ModelPart leftArm;
    @Final
    @Shadow
    private ModelPart rightLeg;
    @Final
    @Shadow
    private ModelPart leftLeg;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/animal/IronGolem;FFFFF)V", cancellable = true)
    public void auram$setAngles(T ironGolemEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        this.head.yRot = i * (float) (Math.PI / 180.0);
        this.head.xRot = j * (float) (Math.PI / 180.0);
        float legPitches = 1.5F * (Math.abs(f % 13.0F - 6.5F) - 3.25F) * 0.30769232F * g;
        this.rightLeg.xRot = -legPitches;
        this.leftLeg.xRot = legPitches;
        this.rightLeg.yRot = 0.0F;
        this.leftLeg.yRot = 0.0F;
    }

    @Inject(at = @At("HEAD"), method = "prepareMobModel(Lnet/minecraft/world/entity/animal/IronGolem;FFF)V", cancellable = true)
    public void auram$animateModel(T ironGolemEntity, float f, float g, float h, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        int attackTicksLeft = ironGolemEntity.getAttackAnimationTick();
        if (attackTicksLeft > 0) {
            float attackAngle = -2.0F + 1.5F * Mth.triangleWave(attackTicksLeft - h, 10.0F);
            this.rightArm.xRot = attackAngle;
            this.leftArm.xRot = attackAngle;
        } else {
            int villagerTicks = ironGolemEntity.getOfferFlowerTick();
            if (villagerTicks > 0) {
                this.rightArm.xRot = -0.8F + 0.025F * Mth.triangleWave(villagerTicks, 70.0F);
                this.leftArm.xRot = 0.0F;
            } else {
                float angle = (Math.abs(f % 13.0F - 6.5F) - 3.25F) * 0.30769232F;
                this.rightArm.xRot = (-0.2F + angle) * g;
                this.leftArm.xRot = (-0.2F - angle) * g;
            }
        }
    }
}

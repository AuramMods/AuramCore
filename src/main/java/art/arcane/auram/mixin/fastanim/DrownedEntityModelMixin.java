// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.DrownedModel;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DrownedModel.class)
public abstract class DrownedEntityModelMixin<T extends Zombie> extends ZombieModel<T> {
    public DrownedEntityModelMixin(ModelPart modelPart) {
        super(modelPart);
    }

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/monster/Zombie;FFFFF)V", cancellable = true)
    public void auram$setAngles(T zombieEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        super.setupAnim(zombieEntity, f, g, h, i, j);
        if (this.leftArmPose == ArmPose.THROW_SPEAR) {
            this.leftArm.xRot = this.leftArm.xRot * 0.5F - (float) Math.PI;
            this.leftArm.yRot = 0.0F;
        }

        if (this.rightArmPose == ArmPose.THROW_SPEAR) {
            this.rightArm.xRot = this.rightArm.xRot * 0.5F - (float) Math.PI;
            this.rightArm.yRot = 0.0F;
        }

        if (this.swimAmount > 0.0F) {
            float sin = (float)Math.sin(0.1F * h);
            float zeroThreeFiveSin = this.swimAmount * 0.35F * sin;
            this.rightArm.xRot = this.rotlerpRad(this.swimAmount, this.rightArm.xRot, (float) (-Math.PI * 4.0 / 5.0)) + zeroThreeFiveSin;
            this.leftArm.xRot = this.rotlerpRad(this.swimAmount, this.leftArm.xRot, (float) (-Math.PI * 4.0 / 5.0)) - zeroThreeFiveSin;
            this.rightArm.zRot = this.rotlerpRad(this.swimAmount, this.rightArm.zRot, -0.15F);
            this.leftArm.zRot = this.rotlerpRad(this.swimAmount, this.leftArm.zRot, 0.15F);
            float legPitch = this.swimAmount * 0.55F * sin;
            this.leftLeg.xRot -= legPitch;
            this.rightLeg.xRot += legPitch;
            this.head.xRot = 0.0F;
        }
    }
}

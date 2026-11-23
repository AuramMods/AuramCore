// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SkeletonModel.class, priority = 900)
public abstract class SkeletonEntityModelMixin<T extends Mob & RangedAttackMob> extends HumanoidModel<T> {
    public SkeletonEntityModelMixin(ModelPart modelPart) {
        super(modelPart);
    }

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/Mob;FFFFF)V", cancellable = true)
    public void auram$setAngles(T mobEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        super.setupAnim(mobEntity, f, g, h, i, j);
        ItemStack itemStack = mobEntity.getMainHandItem();
        if (mobEntity.isAggressive() && (itemStack.isEmpty() || !itemStack.is(Items.BOW))) {
            float k = (float)Math.sin(this.attackTime * (float) Math.PI);
            float l = (float)Math.sin((1.0F - (1.0F - this.attackTime) * (1.0F - this.attackTime)) * (float) Math.PI);
            this.rightArm.zRot = 0.0F;
            this.leftArm.zRot = 0.0F;
            float yaws = 0.1F - k * 0.6F;
            this.rightArm.yRot = -yaws;
            this.leftArm.yRot = yaws;
            this.rightArm.xRot = (float) (-Math.PI / 2);
            this.leftArm.xRot = (float) (-Math.PI / 2);
            float pitches = k * 1.2F - l * 0.4F;
            this.rightArm.xRot -= pitches;
            this.leftArm.xRot -= pitches;
            AnimationUtils.bobArms(this.rightArm, this.leftArm, h);
        }
    }
}

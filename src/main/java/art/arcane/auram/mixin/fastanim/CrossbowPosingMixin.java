// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnimationUtils.class)
public class CrossbowPosingMixin {
    @Inject(at = @At("HEAD"), method = "animateCrossbowCharge(Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/world/entity/LivingEntity;Z)V", cancellable = true)
    private static void auram$charge(ModelPart modelPart, ModelPart modelPart2, LivingEntity livingEntity, boolean bl, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        ModelPart modelPart3 = bl ? modelPart : modelPart2;
        ModelPart modelPart4 = bl ? modelPart2 : modelPart;
        modelPart3.yRot = bl ? -0.8F : 0.8F;
        modelPart3.xRot = -0.97079635F;
        modelPart4.xRot = modelPart3.xRot;
        float f = CrossbowItem.getChargeDuration(livingEntity.getUseItem());
        float g = Mth.clamp(livingEntity.getTicksUsingItem(), 0.0F, f);
        float h = g / f;
        modelPart4.yRot = (0.4F + h * 0.45F) * (bl ? 1 : -1);
        modelPart4.xRot = modelPart4.xRot + h * ((float) (-Math.PI / 2) - modelPart4.xRot);
    }

    @Inject(at = @At("HEAD"), method = "swingWeaponDown(Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/world/entity/Mob;FF)V", cancellable = true)
    private static <T extends Mob> void auram$meleeAttack(ModelPart modelPart, ModelPart modelPart2, T mobEntity, float f, float g, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float h = (float)Math.sin(f * (float) Math.PI);
        float i = (float)Math.sin((1.0F - (1.0F - f) * (1.0F - f)) * (float) Math.PI);
        modelPart.zRot = 0.0F;
        modelPart2.zRot = 0.0F;
        modelPart.yRot = (float) (Math.PI / 20);
        modelPart2.yRot = (float) (-Math.PI / 20);
        float i04 = i * 0.4F;
        if (mobEntity.getMainArm() == HumanoidArm.RIGHT) {
            modelPart.xRot = -1.8849558F + (float)Math.sin(g * 0.09F + (float) (Math.PI / 2)) * 0.15F;
            modelPart2.xRot = -0.0F + (float)Math.sin(g * 0.19F + (float) (Math.PI / 2)) * 0.5F;
            modelPart.xRot += h * 2.2F - i04;
            modelPart2.xRot += h * 1.2F - i04;
        } else {
            modelPart.xRot = -0.0F + (float)Math.sin(g * 0.19F + (float) (Math.PI / 2)) * 0.5F;
            modelPart2.xRot = -1.8849558F + (float)Math.sin(g * 0.09F + (float) (Math.PI / 2)) * 0.15F;
            modelPart.xRot += h * 1.2F - i04;
            modelPart2.xRot += h * 2.2F - i04;
        }

        bobArms(modelPart, modelPart2, g);
    }

    @Inject(at = @At("HEAD"), method = "animateZombieArms(Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;ZFF)V", cancellable = true)
    private static void auram$meleeAttack(ModelPart modelPart, ModelPart modelPart2, boolean bl, float f, float g, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float h = (float)Math.sin(f * (float) Math.PI);
        float oneMinusF = 1.0F - f;
        float i = (float)Math.sin((1.0F - oneMinusF * oneMinusF) * (float) Math.PI);
        modelPart2.zRot = 0.0F;
        modelPart.zRot = 0.0F;
        float yaws = 0.1F - h * 0.6F;
        modelPart2.yRot = -yaws;
        modelPart.yRot = yaws;
        float j = (float) -Math.PI / (bl ? 1.5F : 2.25F);
        modelPart2.xRot = j;
        modelPart.xRot = j;
        float pitches = h * 1.2F - i * 0.4F;
        modelPart2.xRot += pitches;
        modelPart.xRot += pitches;
        bobArms(modelPart2, modelPart, g);
    }

    @Inject(at = @At("HEAD"), method = "bobArms(Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;F)V", cancellable = true)
    private static void auram$swingArms(ModelPart modelPart, ModelPart modelPart2, float f, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float roll = (float)Math.sin(f * 0.09F + (float) (Math.PI / 2)) * 0.05F + 0.05F;
        float pitch = (float)Math.sin(f * 0.067F) * 0.05F;
        modelPart.zRot += roll;
        modelPart.xRot += pitch;
        modelPart2.zRot -= roll;
        modelPart2.xRot -= pitch;
    }

    @Shadow
    private static void bobArms(ModelPart modelPart, ModelPart modelPart2, float f) {
    }
}

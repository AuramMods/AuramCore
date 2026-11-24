package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.vapor.DeathVaporSpread;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class VaporDeathPuffMixin {
    @WrapOperation(method = "tickDeath", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;broadcastEntityEvent(Lnet/minecraft/world/entity/Entity;B)V"))
    private void auram$replaceDeathPoof(Level level, Entity entity, byte event, Operation<Void> original) {
        if (level instanceof ServerLevel serverLevel && event == 60 && entity instanceof LivingEntity living) {
            DeathVaporSpread.emit(serverLevel, living.blockPosition(), living.getMaxHealth());
        } else {
            original.call(level, entity, event);
        }
    }
}

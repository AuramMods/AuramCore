package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.vapor.Vapor;
import art.arcane.auram.vapor.VaporObstacles;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireworkRocketEntity.class)
abstract class FireworkVaporMixin {
    @Inject(method = "explode", at = @At("HEAD"))
    private void auram$burstVapor(CallbackInfo callback) {
        FireworkRocketEntity rocket = (FireworkRocketEntity) (Object) this;
        if (!(rocket.level() instanceof ServerLevel level)) return;
        var fireworks = rocket.getItem().getTagElement("Fireworks");
        int stars = fireworks == null ? 0 : fireworks.getList("Explosions", Tag.TAG_COMPOUND).size();
        if (stars == 0) return;

        BlockPos center = rocket.blockPosition();
        if (VaporObstacles.blocksVapor(level, center, level.getBlockState(center))) {
            center = BlockPos.containing(rocket.xo, rocket.yo, rocket.zo);
        }
        int radius = 2 + Math.min(stars, 3);
        Vapor.pulse(level, center, Math.min(1.0f, 0.45f + 0.2f * stars), radius);
    }
}

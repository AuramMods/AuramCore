package art.arcane.auram.mixin.minecraft;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class FluidReadinessMixin {
    @Redirect(method = "updateFluidHeightAndDoFluidPushing(Ljava/util/function/Predicate;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;touchingUnloadedChunk()Z"))
    private boolean auram$deferFluidScanUntilReady(Entity entity) {
        // Retain the existing guard (including overrides) and its exact bounds.
        if (entity.touchingUnloadedChunk()) return true;
        if (!(entity.level() instanceof ServerLevel level)) return false;
        AABB bounds = entity.getBoundingBox().inflate(1.0);
        int minX = Mth.floor(bounds.minX) >> 4;
        int maxX = Mth.ceil(bounds.maxX) >> 4;
        int minZ = Mth.floor(bounds.minZ) >> 4;
        int maxZ = Mth.ceil(bounds.maxZ) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // hasChunk() accepts a pending FULL ticket. A fluid query must
                // not synchronously wait for that ticket's future to complete.
                if (level.getChunkSource().getChunkNow(x, z) == null) return true;
            }
        }
        return false;
    }
}

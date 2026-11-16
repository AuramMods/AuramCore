package art.arcane.auram.mixin.relics;

import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Entity.class, priority = 1500)
public abstract class FluidCollisionMixin {
    @TargetHandler(mixin = "it.hurts.sskirillss.relics.mixin.EntityMixin", name = "fluidCollision")
    @Redirect(
            method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;")
    )
    private FluidState auram$readLoadedFluid(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return level.getFluidState(pos);
        }
        if (level.isOutsideBuildHeight(pos)) {
            return Fluids.EMPTY.defaultFluidState();
        }

        LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ())
        );
        // Only the unavailable sample is skipped. Relics retains its original
        // collision calculation and FluidCollisionEvent for all loaded samples.
        return chunk == null ? Fluids.EMPTY.defaultFluidState() : chunk.getFluidState(pos);
    }
}

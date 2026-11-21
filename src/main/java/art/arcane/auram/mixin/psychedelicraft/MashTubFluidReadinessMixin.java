package art.arcane.auram.mixin.psychedelicraft;

import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = Entity.class, priority = 1500)
public abstract class MashTubFluidReadinessMixin {
    @Unique private static final String FLUID_MIXIN = "ivorius.psychedelicraft.mixin.EntityMashTubFluidMixin";
    @Unique private static final String GET_VAT =
            "Livorius/psychedelicraft/block/MashTubBlock;getVat(Lnet/minecraft/world/level/BlockGetter;"
                    + "Lnet/minecraft/core/BlockPos;)Livorius/psychedelicraft/block/entity/MashTubBlockEntity;";
    @Unique private ReadyVatBlocks auram$vatBlocks;

    @TargetHandler(mixin = FLUID_MIXIN, name = "psychedelicraft$basinBody")
    @ModifyArg(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = GET_VAT),
            index = 0, remap = false)
    private BlockGetter auram$bodyProbe(BlockGetter world) {
        return auram$readyVatBlocks(world);
    }

    @TargetHandler(mixin = FLUID_MIXIN, name = "psychedelicraft$basinEyes")
    @ModifyArg(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = GET_VAT),
            index = 0, remap = false)
    private BlockGetter auram$eyeProbe(BlockGetter world) {
        return auram$readyVatBlocks(world);
    }

    @Unique
    private BlockGetter auram$readyVatBlocks(BlockGetter world) {
        if (!(world instanceof ServerLevel level)) return world;
        if (auram$vatBlocks == null || auram$vatBlocks.level() != level) {
            auram$vatBlocks = new ReadyVatBlocks(level);
        }
        return auram$vatBlocks;
    }

    // getVat reads both the sampled block and the center of a multipart tub.
    // Restrict these two fluid probes to ready chunks, leaving interaction and dismantling alone.
    public record ReadyVatBlocks(ServerLevel level) implements BlockGetter {
        private LevelChunk readyChunk(BlockPos pos) {
            return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (level.isOutsideBuildHeight(pos)) return Blocks.VOID_AIR.defaultBlockState();
            LevelChunk chunk = readyChunk(pos);
            return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            if (level.isOutsideBuildHeight(pos)) return null;
            LevelChunk chunk = readyChunk(pos);
            return chunk == null ? null : chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }
    }
}

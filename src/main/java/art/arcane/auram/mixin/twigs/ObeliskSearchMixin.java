package art.arcane.auram.mixin.twigs;

import art.arcane.auram.util.TerrainBatch;
import art.arcane.auram.util.TerrainCell;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.ninni.twigs.world.gen.structures.ObeliskStructure", remap = false)
public abstract class ObeliskSearchMixin {
    @Inject(method = "canGenerate", at = @At("HEAD"), cancellable = true)
    private static void auram$sampleObeliskFloor(Structure.GenerationContext context, BlockPos center,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (context.chunkGenerator().getClass() != NoiseBasedChunkGenerator.class) return;
        var generator = (NoiseBasedChunkGenerator) context.chunkGenerator();
        if (!TerrainCell.supports(generator, context.heightAccessor())) return;
        try (TerrainBatch terrain = new TerrainBatch(generator, context.heightAccessor(), context.randomState(), false)) {
            cir.setReturnValue(auram$hasFloor(terrain, center, generator.getSeaLevel()));
        }
    }

    @Unique
    private static boolean auram$hasFloor(TerrainBatch terrain, BlockPos center, int seaLevel) {
        if (seaLevel >= 80) return false;
        for (int dx = -5; dx <= 5; dx++) {
            int x = center.getX() + dx;
            for (int dz = -5; dz <= 5; dz++) {
                int z = center.getZ() + dz;
                BlockState above = terrain.state(x, 80, z);
                for (int y = 80; y > seaLevel; y--) {
                    BlockState floor = terrain.state(x, y - 1, z);
                    if (floor.isSolid() && above.isAir() && terrain.state(x, y + 14, z).isAir()) {
                        return true;
                    }
                    // Moving down one block makes this floor the next block above it.
                    above = floor;
                }
            }
        }
        return false;
    }
}

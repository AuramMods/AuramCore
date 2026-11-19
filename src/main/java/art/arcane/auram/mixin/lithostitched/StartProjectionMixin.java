package art.arcane.auram.mixin.lithostitched;

import art.arcane.auram.util.TerrainBatch;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.worldgen.lithostitched.worldgen.structure.AlternateJigsawGenerator", remap = false)
public abstract class StartProjectionMixin {
    @WrapOperation(method = "lambda$generate$1", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/chunk/ChunkGenerator;getFirstFreeHeight(IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)I", remap = true))
    private static int auram$useStartTerrain(ChunkGenerator generator, int x, int z, Heightmap.Types type,
                                               LevelHeightAccessor height, RandomState state, Operation<Integer> original) {
        TerrainBatch batch = TerrainBatch.current(generator, height, state);
        return batch != null ? batch.height(x, z, type) : original.call(generator, x, z, type, height, state);
    }
}

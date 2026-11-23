package art.arcane.auram.mixin.thaumaturge;

import art.arcane.auram.util.TerrainBatch;
import art.arcane.auram.util.TerrainCell;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Pseudo
@Mixin(targets = "com.leclowndu93150.thaumaturge.content.eldritch.site.ObeliskSiteStructure", remap = false)
public abstract class ObeliskHeightMixin {
    @Shadow @Final private static int MAX_SLOPE;

    @WrapOperation(method = {"findGenerationPoint", "m_214086_"},
            at = @At(value = "INVOKE", remap = true, target =
                    "Lnet/minecraft/world/level/chunk/ChunkGenerator;getFirstOccupiedHeight(IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)I"),
            require = 2)
    private int auram$shareTerrainProbe(ChunkGenerator generator, int x, int z, Heightmap.Types type,
                                        LevelHeightAccessor height, RandomState state, Operation<Integer> original,
                                        @Share("obeliskHeights") LocalRef<TerrainCell.SurfaceHeights> heights,
                                        @Share("obeliskLowest") LocalIntRef lowest,
                                        @Share("obeliskHighest") LocalIntRef highest,
                                        @Cancellable CallbackInfoReturnable<Optional<Structure.GenerationStub>> ci) {
        if (type == Heightmap.Types.WORLD_SURFACE_WG && generator.getClass() == NoiseBasedChunkGenerator.class) {
            TerrainBatch batch = TerrainBatch.current(generator, height, state);
            TerrainCell shared = batch == null ? null : batch.borrowCell(x, z);
            try (TerrainCell owned = batch == null
                    ? TerrainCell.open((NoiseBasedChunkGenerator) generator, x, z, height, state) : null) {
                TerrainCell cell = shared == null ? owned : shared;
                if (cell != null) {
                    TerrainCell.SurfaceHeights sample = cell.surfaceHeights(x, z, height.getMinBuildHeight());
                    int surface = sample.worldSurface();
                    if (sample.oceanFloor() == surface) {
                        boolean first = heights.get() == null;
                        int min = first ? surface : Math.min(lowest.get(), surface);
                        int max = first ? surface : Math.max(highest.get(), surface);
                        // The range only grows. The structure draws RNG after all terrain checks.
                        if (max - min > MAX_SLOPE) ci.setReturnValue(Optional.empty());
                        lowest.set(min);
                        highest.set(max);
                    }
                    heights.set(sample);
                    // TerrainCell records first free height. The structure asks for the occupied block.
                    return surface - 1;
                }
            }
            heights.set(null);
        }
        TerrainCell.SurfaceHeights sampled = heights.get();
        if (sampled != null && type == Heightmap.Types.OCEAN_FLOOR_WG) {
            return sampled.oceanFloor() - 1;
        }
        return original.call(generator, x, z, type, height, state);
    }
}

package art.arcane.auram.mixin.thaumaturge;

import art.arcane.auram.util.TerrainBatch;
import art.arcane.auram.util.TerrainCell;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
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
import org.spongepowered.asm.mixin.injection.Inject;
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
                                        @Share("obeliskHeights") LocalRef<TerrainCell.SurfaceHeights> heights) {
        if (type == Heightmap.Types.WORLD_SURFACE_WG && generator.getClass() == NoiseBasedChunkGenerator.class) {
            TerrainBatch batch = TerrainBatch.current(generator, height, state);
            TerrainCell shared = batch == null ? null : batch.borrowCell(x, z);
            try (TerrainCell owned = batch == null
                    ? TerrainCell.open((NoiseBasedChunkGenerator) generator, x, z, height, state) : null) {
                TerrainCell cell = shared == null ? owned : shared;
                heights.set(cell == null ? null : cell.surfaceHeights(x, z, height.getMinBuildHeight()));
            }
        }
        TerrainCell.SurfaceHeights sampled = heights.get();
        if (sampled != null) {
            // TerrainCell records first free height. The structure asks for the occupied block.
            if (type == Heightmap.Types.WORLD_SURFACE_WG) return sampled.worldSurface() - 1;
            if (type == Heightmap.Types.OCEAN_FLOOR_WG) return sampled.oceanFloor() - 1;
        }
        return original.call(generator, x, z, type, height, state);
    }

    @Inject(method = {"findGenerationPoint", "m_214086_"},
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"), cancellable = true)
    private void auram$rejectSteepTerrain(Structure.GenerationContext context,
                                          CallbackInfoReturnable<Optional<Structure.GenerationStub>> ci,
                                          @Local(name = "lowest") int lowest, @Local(name = "highest") int highest,
                                          @Local(name = "floor") int floor,
                                          @Share("obeliskHeights") LocalRef<TerrainCell.SurfaceHeights> heights) {
        // This point is reached only after the two heights have matched. The range
        // can only grow as more probes are sampled, and RNG runs after the loop.
        if (heights.get() != null && Math.max(highest, floor) - Math.min(lowest, floor) > MAX_SLOPE) {
            ci.setReturnValue(Optional.empty());
        }
    }
}

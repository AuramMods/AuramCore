package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.AquiferReuseProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import static art.arcane.auram.smoke.AquiferReuseProbe.Stage.*;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class AquiferNoiseStageProbeMixin {
    @WrapMethod(method = "doFill")
    private ChunkAccess auramSmoke$noise(Blender blender, StructureManager structures, RandomState random,
                                        ChunkAccess chunk, int minCellY, int cellsY, Operation<ChunkAccess> original) {
        if (!AquiferReuseProbe.TIME_STAGES) return original.call(blender, structures, random, chunk, minCellY, cellsY);
        var scope = AquiferReuseProbe.enter(NOISE, auramSmoke$generator(), random);
        try {
            return original.call(blender, structures, random, chunk, minCellY, cellsY);
        } finally {
            AquiferReuseProbe.leave(scope);
        }
    }

    @WrapMethod(method = "applyCarvers")
    private void auramSmoke$carvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes,
                                    StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step,
                                    Operation<Void> original) {
        if (!AquiferReuseProbe.TIME_STAGES) {
            original.call(region, seed, random, biomes, structures, chunk, step);
            return;
        }
        var scope = AquiferReuseProbe.enter(CARVERS, auramSmoke$generator(), random);
        try {
            original.call(region, seed, random, biomes, structures, chunk, step);
        } finally {
            AquiferReuseProbe.leave(scope);
        }
    }

    @WrapMethod(method = "doCreateBiomes")
    private void auramSmoke$biomes(Blender blender, RandomState random, StructureManager structures,
                                   ChunkAccess chunk, Operation<Void> original) {
        if (!AquiferReuseProbe.TIME_STAGES) {
            original.call(blender, random, structures, chunk);
            return;
        }
        var scope = AquiferReuseProbe.enter(BIOMES, auramSmoke$generator(), random);
        try {
            original.call(blender, random, structures, chunk);
        } finally {
            AquiferReuseProbe.leave(scope);
        }
    }

    @WrapMethod(method = "buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V")
    private void auramSmoke$surface(WorldGenRegion region, StructureManager structures, RandomState random,
                                    ChunkAccess chunk, Operation<Void> original) {
        if (!AquiferReuseProbe.TIME_STAGES) {
            original.call(region, structures, random, chunk);
            return;
        }
        var scope = AquiferReuseProbe.enter(SURFACE, auramSmoke$generator(), random);
        try {
            original.call(region, structures, random, chunk);
        } finally {
            AquiferReuseProbe.leave(scope);
        }
    }

    @Unique
    private NoiseBasedChunkGenerator auramSmoke$generator() {
        return (NoiseBasedChunkGenerator) (Object) this;
    }
}

package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.util.DitheringSurfaceAccess;
import art.arcane.auram.util.SurfaceBiomeSampler;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

@Mixin(SurfaceSystem.class)
public abstract class SurfaceBiomeGetterMixin {
    @ModifyArg(method = "buildSurface", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;<init>(Lnet/minecraft/world/level/levelgen/SurfaceSystem;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/NoiseChunk;Ljava/util/function/Function;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)V"),
            index = 4)
    private Function<BlockPos, Holder<Biome>> auram$reuseSurfaceJitter(Function<BlockPos, Holder<Biome>> original,
                                                                      @Local(argsOnly = true) BiomeManager manager) {
        if (manager.getClass() == BiomeManager.class) return auram$createSampler(manager);
        if (manager instanceof DitheringSurfaceAccess dithering
                && manager.getClass().getName().equals("net.puffish.biomedithermod.DitheringBiomeAccess")) {
            return dithering.auram$surfaceGetter(auram$createSampler(manager));
        }
        return original;
    }

    @Unique
    private static SurfaceBiomeSampler auram$createSampler(BiomeManager manager) {
        BiomeManagerAccess access = (BiomeManagerAccess) manager;
        return new SurfaceBiomeSampler(access.auram$biomeZoomSeed(), access.auram$noiseBiomeSource());
    }
}

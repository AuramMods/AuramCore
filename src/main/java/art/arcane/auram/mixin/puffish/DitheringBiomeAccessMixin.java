package art.arcane.auram.mixin.puffish;

import art.arcane.auram.util.DitheringSurfaceAccess;
import art.arcane.auram.util.SurfaceBiomeSampler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.function.Function;

@Pseudo
@Mixin(targets = "net.puffish.biomedithermod.DitheringBiomeAccess", remap = false)
public abstract class DitheringBiomeAccessMixin implements DitheringSurfaceAccess {
    @Unique
    private SurfaceBiomeSampler auram$activeSurfaceSampler;

    @Override
    public Function<BlockPos, Holder<Biome>> auram$surfaceGetter(SurfaceBiomeSampler sampler) {
        BiomeManager manager = (BiomeManager) (Object) this;
        return pos -> {
            // Puffish makes one manager for each surface build. This getter
            // brackets its original dither step and only redirects the corner scan.
            var previous = auram$activeSurfaceSampler;
            auram$activeSurfaceSampler = sampler;
            try {
                return manager.getBiome(pos);
            } finally {
                auram$activeSurfaceSampler = previous;
            }
        };
    }

    @WrapOperation(method = {"m_204214_", "getBiome"}, remap = false, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/BiomeManager;m_204214_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;", remap = false),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/BiomeManager;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;", remap = false)
    })
    private Holder<Biome> auram$reuseDitheredCorners(@Coerce BiomeManager manager, BlockPos dithered, Operation<Holder<Biome>> original) {
        var sampler = auram$activeSurfaceSampler;
        return sampler == null ? original.call(manager, dithered) : sampler.apply(dithered);
    }
}

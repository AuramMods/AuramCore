package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.util.BiomeFillBounds;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkAccess.class)
public abstract class ChunkBiomeRangeMixin {
    @Redirect(method = "fillBiomesFromNoise", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/LevelHeightAccessor;getMinSection()I"))
    private int auram$firstCommandSection(LevelHeightAccessor height, BiomeResolver resolver, Climate.Sampler sampler) {
        return BiomeFillBounds.minimum(resolver, height.getMinSection());
    }

    @Redirect(method = "fillBiomesFromNoise", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/LevelHeightAccessor;getMaxSection()I"))
    private int auram$lastCommandSection(LevelHeightAccessor height, BiomeResolver resolver, Climate.Sampler sampler) {
        return BiomeFillBounds.maximum(resolver, height.getMaxSection());
    }
}

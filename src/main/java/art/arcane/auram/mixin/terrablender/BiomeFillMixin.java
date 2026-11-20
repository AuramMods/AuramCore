package art.arcane.auram.mixin.terrablender;

import art.arcane.auram.util.TerraBlenderRegions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class BiomeFillMixin {
    @WrapOperation(method = "doCreateBiomes", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/biome/Climate$Sampler;)V"))
    private void auram$reuseRegions(ChunkAccess chunk, BiomeResolver resolver, Climate.Sampler sampler,
                                    Operation<Void> original) {
        TerraBlenderRegions.duringFill(() -> original.call(chunk, resolver, sampler));
    }
}

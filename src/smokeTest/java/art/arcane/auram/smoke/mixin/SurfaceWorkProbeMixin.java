package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.SurfaceWorkProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SurfaceSystem.class)
public abstract class SurfaceWorkProbeMixin {
    @WrapMethod(method = "buildSurface")
    private void auramSmoke$measureSurface(RandomState state, BiomeManager manager, Registry<Biome> biomes, boolean legacy,
                                           WorldGenerationContext context, ChunkAccess chunk, NoiseChunk noise,
                                           SurfaceRules.RuleSource rule, Operation<Void> original) {
        var sample = SurfaceWorkProbe.enter(state, manager);
        try {
            original.call(state, manager, biomes, legacy, context, chunk, noise, rule);
        } finally {
            SurfaceWorkProbe.leave(sample);
        }
    }
}

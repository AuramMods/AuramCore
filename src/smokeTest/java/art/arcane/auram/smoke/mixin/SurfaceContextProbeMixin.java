package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.SurfaceWorkProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$Context")
public abstract class SurfaceContextProbeMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void auramSmoke$observeBiomeGetter(SurfaceSystem surface, RandomState state, ChunkAccess chunk, NoiseChunk noise,
                                                Function<BlockPos, Holder<Biome>> getter, Registry<Biome> biomes,
                                                WorldGenerationContext generation, CallbackInfo ci) {
        SurfaceWorkProbe.observeGetter(state, getter);
    }
}

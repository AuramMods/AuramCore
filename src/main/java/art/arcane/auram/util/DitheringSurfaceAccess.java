package art.arcane.auram.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.function.Function;

public interface DitheringSurfaceAccess {
    Function<BlockPos, Holder<Biome>> auram$surfaceGetter(SurfaceBiomeSampler sampler);
}

package art.arcane.auram.mixin.minecraft;

import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NoiseChunk.NoiseInterpolator.class)
public interface NoiseInterpolatorSliceAccess {
    @Accessor("slice0") double[][] auram$slice0();
    @Accessor("slice1") double[][] auram$slice1();
}

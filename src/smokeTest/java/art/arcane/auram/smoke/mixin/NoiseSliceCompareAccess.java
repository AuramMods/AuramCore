package art.arcane.auram.smoke.mixin;

import java.util.List;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NoiseChunk.class)
public interface NoiseSliceCompareAccess {
    @Accessor("interpolators") List<NoiseChunk.NoiseInterpolator> auramSmoke$interpolators();
}

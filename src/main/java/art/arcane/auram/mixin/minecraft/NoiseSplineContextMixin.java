package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.util.NoiseSplineContext;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(NoiseChunk.class)
public abstract class NoiseSplineContextMixin implements NoiseSplineContext {
    @Unique private DensityFunctions.Spline.Point auram$splinePoint;

    @Override
    public DensityFunctions.Spline.Point auram$splinePoint() {
        if (auram$splinePoint == null) {
            // A Point only holds the context. NoiseChunk updates the sampled
            // coordinates in place, so the same wrapper sees the next sample.
            auram$splinePoint = new DensityFunctions.Spline.Point((DensityFunction.FunctionContext) (Object) this);
        }
        return auram$splinePoint;
    }
}

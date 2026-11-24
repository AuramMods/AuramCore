package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.util.NoiseSplineContext;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DensityFunctions.Spline.class)
public abstract class SplinePointMixin {
    @Redirect(method = "compute", at = @At(value = "NEW", target =
            "(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)Lnet/minecraft/world/level/levelgen/DensityFunctions$Spline$Point;"))
    private DensityFunctions.Spline.Point auram$reusePoint(DensityFunction.FunctionContext context) {
        return context instanceof NoiseSplineContext noise
                ? noise.auram$splinePoint() : new DensityFunctions.Spline.Point(context);
    }
}

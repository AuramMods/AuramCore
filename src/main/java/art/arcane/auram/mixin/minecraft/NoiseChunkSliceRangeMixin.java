package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.util.NoiseRouterCapabilities;
import art.arcane.auram.util.NoiseSliceRange;
import java.lang.invoke.MethodHandles;
import java.util.List;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(NoiseChunk.class)
public abstract class NoiseChunkSliceRangeMixin implements NoiseSliceRange {
    @Unique private static final MethodHandles.Lookup AURAM_SLICE_LOOKUP = MethodHandles.lookup();
    @Shadow @Final private int firstCellX;
    @Shadow @Final private int firstCellZ;
    @Shadow @Final int cellWidth;
    @Shadow @Final int cellCountXZ;
    @Shadow @Final List<NoiseChunk.NoiseInterpolator> interpolators;
    @Shadow @Final private DensityFunction.ContextProvider sliceFillingContextProvider;
    @Shadow private int cellStartBlockX;
    @Shadow private int cellStartBlockZ;
    @Shadow int inCellX;
    @Shadow int inCellZ;
    @Shadow boolean interpolating;
    @Shadow long interpolationCounter;
    @Shadow long arrayInterpolationCounter;

    @Override
    public boolean auram$initializeSlicePrefix(RandomState state, int rows) {
        if (!((NoiseRouterCapabilities) (Object) state).auram$canLimitSliceRows(AURAM_SLICE_LOOKUP)) return false;
        if (interpolating) throw new IllegalStateException("Starting interpolation twice");
        interpolating = true;
        interpolationCounter = 0L;
        var context = new PrefixContext(sliceFillingContextProvider);
        double[] prefix = new double[rows];
        auram$fillPrefix(true, firstCellX, context, prefix);
        auram$fillPrefix(false, firstCellX + 1, context, prefix);
        cellStartBlockX = firstCellX * cellWidth;
        return true;
    }

    @Unique
    private void auram$fillPrefix(boolean first, int cellX, PrefixContext context, double[] prefix) {
        // Match fillSlice's X/Z traversal and cache epochs, using a shorter
        // destination so the usual density functions evaluate only the prefix.
        cellStartBlockX = cellX * cellWidth;
        inCellX = 0;
        for (int z = 0; z <= cellCountXZ; z++) {
            cellStartBlockZ = (firstCellZ + z) * cellWidth;
            inCellZ = 0;
            ++arrayInterpolationCounter;
            for (var interpolator : interpolators) {
                var slices = (NoiseInterpolatorSliceAccess) interpolator;
                double[] target = (first ? slices.auram$slice0() : slices.auram$slice1())[z];
                interpolator.fillArray(prefix, context);
                System.arraycopy(prefix, 0, target, 0, prefix.length);
            }
        }
        ++arrayInterpolationCounter;
    }
}

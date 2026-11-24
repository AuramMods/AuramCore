package art.arcane.auram.util;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;

/** An optional prefix of the interpolation rows for a single bounded terrain query. */
public interface NoiseSliceRange {
    boolean auram$initializeSlicePrefix(RandomState state, int rows);

    record PrefixContext(DensityFunction.ContextProvider original) implements DensityFunction.ContextProvider {
        @Override
        public DensityFunction.FunctionContext forIndex(int index) {
            return original.forIndex(index);
        }

        @Override
        public void fillAllDirectly(double[] values, DensityFunction function) {
            for (int i = 0; i < values.length; i++) {
                values[i] = function.compute(forIndex(i));
            }
        }
    }
}

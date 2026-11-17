package art.arcane.auram.util;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

/** Resolve the vanilla-node classification once per class, not once per DAG edge. */
public final class VanillaDensity {
    private VanillaDensity() { }

    private static final String PREFIX = "net.minecraft.world.level.levelgen.DensityFunctions$";
    private static final ClassValue<Integer> KIND = new ClassValue<>() {
        @Override protected Integer computeValue(Class<?> type) {
            String name = type.getName();
            if (type.getClassLoader() != DensityFunctions.class.getClassLoader() || !name.startsWith(PREFIX)) return 0;
            // Several of these nested classes are protected. Their production
            // class names are stable; classify once without widening access.
            return switch (name.substring(PREFIX.length())) {
                case "Marker", "HolderHolder", "BeardifierMarker", "BlendAlpha", "BlendOffset" -> 1;
                default -> 2;
            };
        }
    };

    public static boolean isBuiltin(DensityFunction function) {
        return KIND.get(function.getClass()) != 0;
    }

    public static boolean isNoopWrapper(DensityFunction function) {
        // NoiseChunk.wrapNew changes only these vanilla nodes. Other built-in
        // functions can retain their own identity instead of being structurally
        // interned in its HashMap (whose record hashes traverse the whole DAG).
        return KIND.get(function.getClass()) == 2;
    }
}

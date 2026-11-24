package art.arcane.auram.util;

import java.lang.invoke.MethodHandles;

/** Cached properties of the seeded density graph. */
public interface NoiseRouterCapabilities {
    boolean auram$canReuseClimateMapping(MethodHandles.Lookup lookup);

    boolean auram$canLimitSliceRows(MethodHandles.Lookup lookup);
}

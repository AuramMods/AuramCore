package art.arcane.auram.util;

import java.lang.invoke.MethodHandles;

/** A seeded RandomState's climate graph classification. */
public interface ClimateMappingAccess {
    boolean auram$canReuseClimateMapping(MethodHandles.Lookup lookup);
}

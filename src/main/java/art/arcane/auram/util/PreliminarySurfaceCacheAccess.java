package art.arcane.auram.util;

import net.minecraft.world.level.levelgen.NoiseSettings;

import java.lang.invoke.MethodHandles;

public interface PreliminarySurfaceCacheAccess {
    PreliminarySurfaceCache auram$preliminarySurfaceCache(NoiseSettings settings, MethodHandles.Lookup lookup);
}

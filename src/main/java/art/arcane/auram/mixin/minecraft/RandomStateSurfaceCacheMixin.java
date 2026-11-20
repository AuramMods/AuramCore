package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.util.PreliminaryDensity;
import art.arcane.auram.util.PreliminarySurfaceCache;
import art.arcane.auram.util.PreliminarySurfaceCacheAccess;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.lang.invoke.MethodHandles;

@Mixin(RandomState.class)
public abstract class RandomStateSurfaceCacheMixin implements PreliminarySurfaceCacheAccess {
    @Unique private volatile boolean auram$surfaceCacheInitialized;
    @Unique private PreliminarySurfaceCache auram$surfaceCache;

    @Override
    public PreliminarySurfaceCache auram$preliminarySurfaceCache(NoiseSettings settings, MethodHandles.Lookup lookup) {
        if (!auram$surfaceCacheInitialized) auram$initializeSurfaceCache(settings, lookup);
        var cache = auram$surfaceCache;
        return cache != null && cache.matches(settings) ? cache : null;
    }

    @Unique
    private synchronized void auram$initializeSurfaceCache(NoiseSettings settings, MethodHandles.Lookup lookup) {
        if (auram$surfaceCacheInitialized) return;
        var root = ((RandomState) (Object) this).router().initialDensityWithoutJaggedness();
        if (PreliminaryDensity.canShare(root, lookup)) auram$surfaceCache = new PreliminarySurfaceCache(settings);
        auram$surfaceCacheInitialized = true;
    }
}

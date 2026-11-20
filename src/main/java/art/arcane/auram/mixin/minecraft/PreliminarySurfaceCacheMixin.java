package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.util.PreliminarySurfaceCache;
import art.arcane.auram.util.PreliminarySurfaceCacheAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.MethodHandles;

@Mixin(NoiseChunk.class)
public abstract class PreliminarySurfaceCacheMixin {
    @Unique private static final MethodHandles.Lookup AURAM_LOOKUP = MethodHandles.lookup();
    @Unique private PreliminarySurfaceCache auram$surfaceCache;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void auram$bindSurfaceCache(int cellsXZ, RandomState random, int originX, int originZ,
                                        NoiseSettings settings, DensityFunctions.BeardifierOrMarker beardifier,
                                        NoiseGeneratorSettings generatorSettings, Aquifer.FluidPicker fluids,
                                        Blender blender, CallbackInfo ci) {
        if (generatorSettings.isAquifersEnabled() && blender == Blender.empty()) {
            auram$surfaceCache = ((PreliminarySurfaceCacheAccess) (Object) random)
                    .auram$preliminarySurfaceCache(settings, AURAM_LOOKUP);
        }
    }

    @Inject(method = "computePreliminarySurfaceLevel", at = @At("HEAD"), cancellable = true)
    private void auram$reuseHeight(long column, CallbackInfoReturnable<Integer> cir) {
        if (auram$surfaceCache == null) return;
        var entry = auram$surfaceCache.lookup(column);
        if (entry != null) cir.setReturnValue(entry.height());
    }

    @Inject(method = "computePreliminarySurfaceLevel", at = @At("RETURN"))
    private void auram$rememberHeight(long column, CallbackInfoReturnable<Integer> cir) {
        if (auram$surfaceCache != null) auram$surfaceCache.record(column, cir.getReturnValueI());
    }
}

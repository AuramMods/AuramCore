package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.util.NoiseRouterCapabilities;
import art.arcane.auram.util.NoiseMappingScope;
import art.arcane.auram.util.VanillaDensity;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Mixin(NoiseChunk.class)
public abstract class NoiseChunkMappingMixin {
    @Unique private static final MethodHandles.Lookup AURAM_NOISE_LOOKUP = MethodHandles.lookup();
    @Unique private boolean auram$sharedTerrainGraph;
    @Unique private NoiseRouter auram$originalClimateRouter;
    @Unique private NoiseRouter auram$mappedClimateRouter;

    @Group(name="auram$wrapperLookup",min=1)
    @Redirect(method="wrap",at=@At(value="INVOKE",target=
            "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),require=0)
    private Object auram$skipNoopInterning(Map<DensityFunction,DensityFunction> cache, Object key,
                                          Function<DensityFunction,DensityFunction> factory) {
        DensityFunction function=(DensityFunction)key;
        return auram$sharedTerrainGraph && VanillaDensity.isNoopWrapper(function)
                ? function : cache.computeIfAbsent(function,factory);
    }

    // ModernFix replaces computeIfAbsent with get/put. Cover that installed
    // implementation too, without allocating a cancellable callback per node.
    @Group(name="auram$wrapperLookup",min=1)
    @Redirect(method="wrap",at=@At(value="INVOKE",target=
            "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"),require=0)
    private Object auram$skipNoopLookup(Map<DensityFunction,DensityFunction> cache, Object key) {
        DensityFunction function=(DensityFunction)key;
        return auram$sharedTerrainGraph && VanillaDensity.isNoopWrapper(function) ? function : cache.get(function);
    }
    @Redirect(method="<init>",at=@At(value="INVOKE",target=
            "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private NoiseRouter auram$mapRouter(NoiseRouter router, DensityFunction.Visitor visitor,
            int cellsXZ, RandomState random, int originX, int originZ, NoiseSettings settings,
            DensityFunctions.BeardifierOrMarker beardifier, NoiseGeneratorSettings generatorSettings,
            Aquifer.FluidPicker fluids, Blender blender) {
        // Vanilla Nether/End routers have constant depth and small graphs. Keep
        // their original traversal rather than adding memoization overhead.
        auram$sharedTerrainGraph = router.depth().minValue() != router.depth().maxValue();
        NoiseRouter mapped = auram$sharedTerrainGraph ? NoiseMappingScope.map(router,visitor) : router.mapAll(visitor);
        if (auram$sharedTerrainGraph && cellsXZ > 1 && blender == Blender.empty()
                && ((NoiseRouterCapabilities) (Object) random).auram$canReuseClimateMapping(AURAM_NOISE_LOOKUP)) {
            auram$originalClimateRouter = router;
            auram$mappedClimateRouter = mapped;
        }
        return mapped;
    }


    @Inject(method = "cachedClimateSampler", at = @At("HEAD"), cancellable = true)
    private void auram$reuseMappedClimate(NoiseRouter router, List<Climate.ParameterPoint> spawnTargets,
                                          CallbackInfoReturnable<Climate.Sampler> result) {
        if (auram$originalClimateRouter != router || auram$mappedClimateRouter == null) return;
        // These are the same wrappers owned by this NoiseChunk. The next
        // climate query can use the standard path without retaining the router.
        NoiseRouter mapped = auram$mappedClimateRouter;
        auram$originalClimateRouter = null;
        auram$mappedClimateRouter = null;
        result.setReturnValue(new Climate.Sampler(mapped.temperature(), mapped.vegetation(), mapped.continents(),
                mapped.erosion(), mapped.depth(), mapped.ridges(), spawnTargets));
    }
    @Redirect(method="<init>",at=@At(value="INVOKE",target=
            "Lnet/minecraft/world/level/levelgen/DensityFunction;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/DensityFunction;"))
    private DensityFunction auram$mapFinalDensity(DensityFunction function,DensityFunction.Visitor visitor) {
        return auram$sharedTerrainGraph ? NoiseMappingScope.map(function,visitor) : function.mapAll(visitor);
    }
}

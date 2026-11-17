package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.util.NoiseMappingScope;
import art.arcane.auram.util.VanillaDensity;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.function.Function;

@Mixin(NoiseChunk.class)
public abstract class NoiseChunkMappingMixin {
    @Unique private boolean auram$sharedTerrainGraph;

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
    private NoiseRouter auram$mapRouter(NoiseRouter router,DensityFunction.Visitor visitor) {
        // Vanilla Nether/End routers have constant depth and small graphs. Keep
        // their original traversal rather than adding memoization overhead.
        auram$sharedTerrainGraph = router.depth().minValue() != router.depth().maxValue();
        return auram$sharedTerrainGraph ? NoiseMappingScope.map(router,visitor) : router.mapAll(visitor);
    }
    @Redirect(method="<init>",at=@At(value="INVOKE",target=
            "Lnet/minecraft/world/level/levelgen/DensityFunction;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/DensityFunction;"))
    private DensityFunction auram$mapFinalDensity(DensityFunction function,DensityFunction.Visitor visitor) {
        return auram$sharedTerrainGraph ? NoiseMappingScope.map(function,visitor) : function.mapAll(visitor);
    }
}

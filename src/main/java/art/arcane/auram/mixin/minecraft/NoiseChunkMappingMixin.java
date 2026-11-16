package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.util.NoiseMappingScope;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NoiseChunk.class)
public abstract class NoiseChunkMappingMixin {
    @Unique private boolean auram$sharedTerrainGraph;
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

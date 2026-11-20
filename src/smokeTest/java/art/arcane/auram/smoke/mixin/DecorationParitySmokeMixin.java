package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.SurfaceMapBenchSmoke;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class DecorationParitySmokeMixin {
    @Unique private static final boolean AURAM_SKIP_DECORATION = SurfaceMapBenchSmoke.WORLDGEN
            && Boolean.getBoolean("auram.smoke.compareBeforeDecoration");

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"), cancellable = true)
    private void auramSmoke$skipDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures,
                                           CallbackInfo ci) {
        if (AURAM_SKIP_DECORATION) ci.cancel();
    }
}

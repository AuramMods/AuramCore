package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.map.SurfaceMaps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChunkStatus.class)
abstract class SurfaceMapGenerationMixin {
    // This FEATURES task tail follows heightmap initialization, biome decoration and border ticks.
    @Inject(method = "lambda$static$11(Lnet/minecraft/world/level/chunk/ChunkStatus;"
            + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;"
            + "Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;)V", at = @At("TAIL"))
    private static void auram$captureDecoratedSurface(ChunkStatus status, ServerLevel level, ChunkGenerator generator,
                                                     List<ChunkAccess> neighbors, ChunkAccess chunk, CallbackInfo ci) {
        SurfaceMaps.generated(level, chunk);
    }
}

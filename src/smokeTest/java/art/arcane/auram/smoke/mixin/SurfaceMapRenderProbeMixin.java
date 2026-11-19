package art.arcane.auram.smoke.mixin;

import art.arcane.auram.map.client.SurfaceMapClient;
import art.arcane.auram.smoke.SurfaceMapSmokeClient;
import journeymap.client.model.ChunkMD;
import journeymap.client.model.MapType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "journeymap.client.model.ChunkMD", remap = false)
abstract class SurfaceMapRenderProbeMixin {
    @Inject(method = "setRendered", at = @At("HEAD"))
    private void auram$recordRenderDecision(MapType mapType, CallbackInfoReturnable<Long> cir) {
        if (!SurfaceMapSmokeClient.ENABLED || !SurfaceMapClient.active()) {
            return;
        }
        var chunk = ((ChunkMD) (Object) this).getCoord();
        if (chunk == null || mapType.dimension == null) {
            return;
        }
        boolean covered = SurfaceMapClient.covered(mapType.dimension.location(), chunk.x, chunk.z);
        boolean skipped = StackWalker.getInstance().walk(stack ->
                stack.limit(8).anyMatch(frame -> frame.getMethodName().contains("auram$preferServerTiles")));
        if (covered) {
            System.out.printf("SURFACE_TEST RENDER %d %d %s%n", chunk.x, chunk.z, skipped ? "skipped" : "local");
        }
    }
}

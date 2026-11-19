package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.map.SurfaceTile;
import art.arcane.auram.map.client.FtbChunksSurface;
import art.arcane.auram.map.client.SurfaceMapClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftbchunks.client.map.MapMode;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftbchunks.client.map.RenderMapImageTask;
import dev.ftb.mods.ftblibrary.math.XZ;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderMapImageTask.class, remap = false)
abstract class RenderMapSurfaceMixin {
    @Shadow @Final private MapRegion region;
    @Shadow private static boolean alwaysRenderChunksOnMap;
    @Unique private SurfaceTile[] auram$tiles;

    @Inject(method = "runMapTask", at = @At("HEAD"))
    private void auram$readSurface(CallbackInfo ci) {
        auram$tiles = SurfaceMapClient.active() ? ((MapRegionSurfaceAccess) region).auram$tiles() : null;
    }

    @ModifyVariable(method = "runMapTask", at = @At("STORE"), ordinal = 0)
    private MapMode auram$surfaceLayer(MapMode mode) {
        return SurfaceMapClient.active() ? MapMode.NONE : mode;
    }

    @WrapOperation(method = "runMapTask", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbchunks/client/map/MapRegion;getMapChunk(Ldev/ftb/mods/ftblibrary/math/XZ;)Ldev/ftb/mods/ftbchunks/client/map/MapChunk;"))
    private MapChunk auram$skipCoveredRaster(MapRegion region, XZ chunk, Operation<MapChunk> original) {
        return auram$tiles != null && FtbChunksSurface.hasChunk(auram$tiles, chunk.x(), chunk.z())
                ? null : original.call(region, chunk);
    }

    @Inject(method = "runMapTask", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbchunks/client/map/MapRegion;afterImageRenderTask()V"))
    private void auram$paintSurface(CallbackInfo ci) {
        if (auram$tiles != null && SurfaceMapClient.active()) {
            FtbChunksSurface.paint(region, auram$tiles, alwaysRenderChunksOnMap);
        }
    }
}

package art.arcane.auram.mixin.journeymap;

import art.arcane.auram.map.client.SurfaceMapClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import journeymap.client.cartography.ChunkRenderController;
import journeymap.client.model.ChunkMD;
import journeymap.client.model.MapType;
import journeymap.client.model.RegionCoord;
import journeymap.client.task.multi.BaseMapTask;
import journeymap.common.nbt.RegionData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BaseMapTask.class, remap = false)
abstract class BaseMapTaskMixin {
    @WrapOperation(method = "performTask", at = @At(value = "INVOKE", target =
            "Ljourneymap/client/cartography/ChunkRenderController;renderChunk(Ljourneymap/client/model/RegionCoord;"
                    + "Ljourneymap/client/model/MapType;Ljourneymap/client/model/ChunkMD;Ljourneymap/common/nbt/RegionData;)Z"))
    private boolean auram$preferServerTiles(ChunkRenderController renderer, RegionCoord region, MapType layer,
                                             ChunkMD chunk, RegionData data, Operation<Boolean> render) {
        if (SurfaceMapClient.active()) {
            if (!layer.isDayOrNight() && !layer.isSurface()) {
                return false;
            }
            var coordinates = chunk.getCoord();
            if (SurfaceMapClient.covered(region.dimension.location(), coordinates.x, coordinates.z)) {
                return false;
            }
        }
        return render.call(renderer, region, layer, chunk, data);
    }
}

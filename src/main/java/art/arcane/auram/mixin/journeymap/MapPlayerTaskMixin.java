package art.arcane.auram.mixin.journeymap;

import art.arcane.auram.map.client.SurfaceMapClient;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import journeymap.client.model.EntityDTO;
import journeymap.client.model.MapType;
import journeymap.client.task.multi.MapPlayerTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MapPlayerTask.class, remap = false)
abstract class MapPlayerTaskMixin {
    @ModifyExpressionValue(method = "create", at = @At(value = "FIELD",
            target = "Ljourneymap/client/model/EntityDTO;underground:Ljava/lang/Boolean;"))
    private static Boolean auram$useSurfaceWhileUnderground(Boolean underground) {
        return SurfaceMapClient.active() ? Boolean.FALSE : underground;
    }

    @WrapOperation(method = "create", at = @At(value = "INVOKE",
            target = "Ljourneymap/client/model/MapType;night(Ljourneymap/client/model/EntityDTO;)Ljourneymap/client/model/MapType;"))
    private static MapType auram$useOneSurfaceLayer(EntityDTO player, Operation<MapType> night) {
        return SurfaceMapClient.active() ? MapType.day(player) : night.call(player);
    }
}

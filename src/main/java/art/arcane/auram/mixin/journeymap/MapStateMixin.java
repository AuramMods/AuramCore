package art.arcane.auram.mixin.journeymap;

import art.arcane.auram.map.client.SurfaceMapClient;
import journeymap.client.model.MapState;
import journeymap.client.model.MapType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MapState.class, remap = false)
abstract class MapStateMixin {
    @Inject(method = "getMapType", at = @At("RETURN"), cancellable = true)
    private void auram$displaySurface(CallbackInfoReturnable<MapType> cir) {
        MapType type = cir.getReturnValue();
        if (SurfaceMapClient.active() && type != null && type.dimension != null && !type.isDay()) {
            MapType surface = MapType.day(type.dimension);
            if (surface.isAllowed()) {
                cir.setReturnValue(surface);
            }
        }
    }

    @Inject(method = "setMapType(Ljourneymap/client/model/MapType;)Ljourneymap/client/model/MapType;",
            at = @At("HEAD"), cancellable = true)
    private void auram$keepSurfaceSelected(MapType type, CallbackInfoReturnable<MapType> cir) {
        if (SurfaceMapClient.active() && type.dimension != null && !type.isDay()) {
            MapType surface = MapType.day(type.dimension);
            if (surface.isAllowed()) {
                cir.setReturnValue(surface);
            }
        }
    }
}

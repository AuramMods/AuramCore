package art.arcane.auram.smoke.mixin;

import art.arcane.auram.map.SurfaceMapNetwork;
import art.arcane.auram.map.SurfaceMapView;
import art.arcane.auram.smoke.SurfaceMapSmokeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SurfaceMapNetwork.class, remap = false)
abstract class SurfaceMapWireProbeMixin {
    @Inject(method = "sendView", at = @At("HEAD"))
    private static void auram$recordVisibleTiles(SurfaceMapView view, CallbackInfo ci) {
        SurfaceMapSmokeClient.view(view);
    }
}

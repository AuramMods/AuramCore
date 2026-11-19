package art.arcane.auram.smoke.mixin;

import art.arcane.auram.map.SurfaceMapNetwork;
import art.arcane.auram.map.client.SurfaceMapClient;
import art.arcane.auram.smoke.SurfaceMapSmokeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SurfaceMapClient.class, remap = false)
abstract class SurfaceMapReceptionProbeMixin {
    @Inject(method = "tile", at = @At("HEAD"))
    private void auram$recordDownloads(SurfaceMapNetwork.Tile tile, CallbackInfo ci) {
        SurfaceMapSmokeClient.tile(tile);
    }
}

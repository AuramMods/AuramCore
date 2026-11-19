package art.arcane.auram.mixin.journeymap;

import art.arcane.auram.map.client.SurfaceMapClient;
import journeymap.client.feature.Feature;
import journeymap.client.feature.FeatureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FeatureManager.class, remap = false)
abstract class FeatureManagerMixin {
    @Inject(method = "isAllowed(Ljourneymap/client/feature/Feature;)Z", at = @At("HEAD"), cancellable = true)
    private void auram$surfaceOnly(Feature feature, CallbackInfoReturnable<Boolean> cir) {
        if (SurfaceMapClient.active()
                && (feature == Feature.MapCaves || feature == Feature.MapTopo || feature == Feature.MapBiome)) {
            cir.setReturnValue(false);
        }
    }
}

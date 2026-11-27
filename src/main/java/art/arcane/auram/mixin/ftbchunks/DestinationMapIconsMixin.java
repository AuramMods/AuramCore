package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.waystone.DestinationMapClient;
import dev.ftb.mods.ftbchunks.api.client.event.MapIconEvent;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import dev.ftb.mods.ftbchunks.client.FTBChunksClientConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FTBChunksClient.class, remap = false)
abstract class DestinationMapIconsMixin {
    @Inject(method = "mapIcons", at = @At("TAIL"))
    private void auram$addHomeAndWarpIcons(MapIconEvent event, CallbackInfo ci) {
        if (!event.getMapType().isMinimap() || FTBChunksClientConfig.MINIMAP_WAYPOINTS.get()) {
            DestinationMapClient.addIcons(event);
        }
    }
}

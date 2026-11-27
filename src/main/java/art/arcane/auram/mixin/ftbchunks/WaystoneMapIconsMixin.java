package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.waystone.DestinationMapClient;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.ftb.mods.ftbchunks.api.client.event.MapIconEvent;
import dev.ftb.mods.ftbchunks.api.client.icon.MapIcon;
import dev.ftb.mods.ftbchunks.client.FTBChunksClientConfig;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbxmodcompat.ftbchunks.waystones.WaystonesCommon", remap = false)
abstract class WaystoneMapIconsMixin {
    @WrapWithCondition(method = "mapWidgets", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbchunks/api/client/event/MapIconEvent;add(Ldev/ftb/mods/ftbchunks/api/client/icon/MapIcon;)V"))
    private static boolean auram$skipDuplicateWaystoneIcon(MapIconEvent event, MapIcon icon) {
        if (event.getMapType().isMinimap() && !FTBChunksClientConfig.MINIMAP_WAYPOINTS.get()) return true;
        return !DestinationMapClient.hasDestinationAt(event, BlockPos.containing(icon.getPos(1)));
    }
}

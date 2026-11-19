package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.map.client.MapRegionSurfaceAccess;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(value = MapDimension.class, remap = false)
abstract class MapDimensionSurfaceMixin {
    @ModifyVariable(method = "writeData", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private List<MapRegion> auram$writeNativeRegions(List<MapRegion> regions) {
        // The native index stores region coordinates as bytes. Server-only regions are reconstructed from Auram's cache.
        return regions.stream().filter(region -> !((MapRegionSurfaceAccess) region).auram$isStreamOnly()).toList();
    }
}

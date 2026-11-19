package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.map.SurfaceTile;
import art.arcane.auram.map.client.SurfaceMapClient;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MapRegion.class, remap = false)
abstract class MapRegionSurfaceMixin implements MapRegionSurfaceAccess {
    @Unique private volatile SurfaceTile[] auram$surfaceTiles;

    @Override
    public void auram$install(int slot, SurfaceTile tile) {
        MapRegion region = (MapRegion) (Object) this;
        synchronized (region.dimension.getManager().lock) {
            var previous = auram$surfaceTiles;
            var next = previous == null ? new SurfaceTile[16] : previous.clone();
            next[slot] = tile;
            auram$surfaceTiles = next;
            region.update(false);
        }
    }

    @Override
    public SurfaceTile[] auram$tiles() {
        return auram$surfaceTiles;
    }

    @Inject(method = "releaseMapImage", at = @At("HEAD"))
    private void auram$releaseSurface(CallbackInfo ci) {
        MapRegion region = (MapRegion) (Object) this;
        synchronized (region.dimension.getManager().lock) {
            var previous = auram$surfaceTiles;
            auram$surfaceTiles = null;
            if (previous != null) {
                for (int slot = 0; slot < previous.length; slot++) {
                    if (previous[slot] != null) {
                        var key = new SurfaceTile.Key(region.dimension.dimension.location(),
                                region.pos.x() * 4 + slot % 4, region.pos.z() * 4 + slot / 4);
                        SurfaceMapClient.ftbImageReleased(key);
                    }
                }
            }
        }
    }
}

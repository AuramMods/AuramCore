package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.map.SurfaceTile;

public interface MapRegionSurfaceAccess {
    void auram$install(int slot, SurfaceTile tile);

    SurfaceTile[] auram$tiles();
}

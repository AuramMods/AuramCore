package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.map.SurfaceMapView;

public interface RegionMapSurfaceAccess {
    SurfaceMapView auram$view();

    void auram$addedRegion();
}

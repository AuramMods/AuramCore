package art.arcane.auram.map.client;

import art.arcane.auram.map.SurfaceMapView;

public interface RegionMapSurfaceAccess {
    int auram$zoomLayoutSize();

    double auram$zoomScale();

    void auram$zoomTo(int size, double mouseX, double mouseY, long now);

    void auram$advanceZoom(long now);

    SurfaceMapView auram$view();

    void auram$addedRegion();
}

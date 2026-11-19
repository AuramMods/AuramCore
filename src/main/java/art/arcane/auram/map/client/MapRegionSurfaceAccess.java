package art.arcane.auram.map.client;

import art.arcane.auram.map.SurfaceTile;

public interface MapRegionSurfaceAccess {
    void auram$markStreamOnly();

    boolean auram$isStreamOnly();

    /** Returns true on the first shared tile installed in this image. */
    boolean auram$install(int slot, SurfaceTile tile);

    SurfaceTile[] auram$tiles();
}

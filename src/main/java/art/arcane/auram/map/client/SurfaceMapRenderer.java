package art.arcane.auram.map.client;

import art.arcane.auram.map.SurfaceMapView;
import art.arcane.auram.map.SurfaceTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

interface SurfaceMapRenderer {
    enum Kind { JOURNEYMAP, FTB_CHUNKS }

    Kind kind();

    /** Null means this mapper has no visible view in the active world dimension. */
    SurfaceMapView view(Minecraft minecraft, LocalPlayer player);

    boolean apply(SurfaceTile.Key key, SurfaceTile tile);

    static SurfaceMapView blocks(ResourceLocation dimension, int x, int z,
                                 int minX, int minZ, int maxX, int maxZ) {
        int tileBlocks = 16 * SurfaceTile.CHUNKS;
        int centerX = Math.floorDiv(x, tileBlocks);
        int centerZ = Math.floorDiv(z, tileBlocks);
        int radius = SurfaceMapView.MAX_RADIUS;
        return new SurfaceMapView(dimension, centerX, centerZ,
                Math.max(centerX - radius, Math.min(centerX, Math.floorDiv(minX, tileBlocks))),
                Math.max(centerZ - radius, Math.min(centerZ, Math.floorDiv(minZ, tileBlocks))),
                Math.min(centerX + radius, Math.max(centerX, Math.floorDiv(maxX, tileBlocks))),
                Math.min(centerZ + radius, Math.max(centerZ, Math.floorDiv(maxZ, tileBlocks))));
    }
}

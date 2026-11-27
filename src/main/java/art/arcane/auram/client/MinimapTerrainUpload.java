package art.arcane.auram.client;

import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftblibrary.math.XZ;

public final class MinimapTerrainUpload {
    private static final int CHUNK_SIZE = 16;
    private static final int REGION_SIZE = 512;

    private MinimapTerrainUpload() {}

    public static void upload(MapDimension dimension, int chunkX, int chunkZ, int span, boolean blur) {
        int startX = (chunkX - span / 2) * CHUNK_SIZE;
        int startZ = (chunkZ - span / 2) * CHUNK_SIZE;
        int endX = startX + span * CHUNK_SIZE;
        int endZ = startZ + span * CHUNK_SIZE;

        // Keep the region images alive throughout the render-thread uploads.
        synchronized (dimension.getManager().lock) {
            for (int z = startZ; z < endZ;) {
                int regionZ = z & (REGION_SIZE - 1);
                int height = Math.min(REGION_SIZE - regionZ, endZ - z);
                for (int x = startX; x < endX;) {
                    int regionX = x & (REGION_SIZE - 1);
                    int width = Math.min(REGION_SIZE - regionX, endX - x);
                    var image = dimension.getRegion(XZ.regionFromChunk(x >> 4, z >> 4)).getRenderedMapImage();
                    image.upload(0, x - startX, z - startZ, regionX, regionZ, width, height, blur, false, false, false);
                    x += width;
                }
                z += height;
            }
        }
    }
}

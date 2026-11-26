package art.arcane.auram.client.menu;

import com.mojang.blaze3d.platform.NativeImage;
import dev.ftb.mods.ftbchunks.FTBChunksWorldConfig;
import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

final class FtbMapMenu {
    private FtbMapMenu() {}

    static boolean available(LocalPlayer player) {
        return FTBChunksWorldConfig.playerHasMapStage(player);
    }

    static boolean matches(Screen screen) {
        return screen instanceof ScreenWrapper wrapper && wrapper.getGui() instanceof LargeMapScreen;
    }

    static void open() {
        LargeMapScreen.openMap();
    }

    static MapMenuView snapshot(LocalPlayer player, double x, double z, int width, int height,
                                int[] pixels, int columns, int rows) {
        var map = MapDimension.getCurrent().orElse(null);
        if (map == null || !map.dimension.equals(player.level().dimension()) || !available(player)) return null;

        // Keep render workers from releasing a region image while we sample it. Only existing
        // regions are checked, and getRenderedMapImage queues any missing raster work.
        synchronized (map.getManager().lock) {
            var raster = new Raster(map);
            int zoom = MapMenuView.widestCoveredZoom(width, candidate -> raster.covers(
                    x, z, MapMenuView.halfSpan(width, candidate), MapMenuView.halfSpan(height, candidate)));
            if (zoom == 0) return null;
            double spanX = MapMenuView.halfSpan(width, zoom);
            double spanZ = MapMenuView.halfSpan(height, zoom);
            for (int row = 0; row < rows; row++) {
                int worldZ = Mth.floor(z + (2 * (row + 0.5) / rows - 1) * spanZ);
                for (int column = 0; column < columns; column++) {
                    int worldX = Mth.floor(x + (2 * (column + 0.5) / columns - 1) * spanX);
                    int pixel = raster.pixel(worldX, worldZ);
                    pixels[row * columns + column] = (pixel >>> 24) == 0
                            ? raster.pixel((worldX & ~15) + 8, (worldZ & ~15) + 8) : pixel;
                }
            }
            return new MapMenuView(map.dimension, x, z, zoom);
        }
    }

    private static final class Raster {
        private final MapDimension map;
        private final Map<XZ, NativeImage> images = new HashMap<>();

        private Raster(MapDimension map) {
            this.map = map;
        }

        private int pixel(int x, int z) {
            var key = XZ.regionFromBlock(x, z);
            if (!images.containsKey(key)) {
                var region = map.getRegions().get(key);
                images.put(key, region == null ? null : region.getRenderedMapImage());
            }
            var image = images.get(key);
            return image == null ? 0 : image.getPixelRGBA(x & 511, z & 511);
        }

        private boolean covers(double x, double z, double spanX, double spanZ) {
            int firstX = Mth.floor(x - spanX) >> 4;
            int lastX = (Mth.ceil(x + spanX) - 1) >> 4;
            int firstZ = Mth.floor(z - spanZ) >> 4;
            int lastZ = (Mth.ceil(z + spanZ) - 1) >> 4;
            for (int chunkZ = firstZ; chunkZ <= lastZ; chunkZ++) {
                for (int chunkX = firstX; chunkX <= lastX; chunkX++) {
                    if ((pixel((chunkX << 4) + 8, (chunkZ << 4) + 8) >>> 24) == 0) return false;
                }
            }
            return true;
        }
    }
}

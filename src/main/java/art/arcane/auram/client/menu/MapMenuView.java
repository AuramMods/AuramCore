package art.arcane.auram.client.menu;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.function.IntPredicate;

public record MapMenuView(ResourceKey<Level> dimension, double x, double z, int zoom) {
    public static final int MAX_ZOOM = 32768;
    private static final int BLOCKS_ACROSS = 16 * 16;

    static int widestCoveredZoom(int width, IntPredicate covered) {
        // FTB's 512-block tiles occupy two GUI pixels per unit of zoom.
        int widest = Math.min(MAX_ZOOM, (width * 256 + BLOCKS_ACROSS - 1) / BLOCKS_ACROSS);
        if (covered.test(widest)) return widest;
        if (!covered.test(MAX_ZOOM)) return 0;

        int low = widest + 1;
        int high = MAX_ZOOM;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (covered.test(mid)) high = mid;
            else low = mid + 1;
        }
        return low;
    }

    static double halfSpan(int pixels, int zoom) {
        return pixels * 128.0 / zoom;
    }
}

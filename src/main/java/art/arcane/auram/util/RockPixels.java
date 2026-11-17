package art.arcane.auram.util;

import java.util.function.IntBinaryOperator;

/** Pixel operations use NativeImage's ABGR order, without requiring a client. */
public final class RockPixels {
    private RockPixels() { }

    public static int hostPixel(int mask, int host) {
        double shade = 0.55 + (mask & 255) / 200.0;
        int r = Math.min(255, (int) Math.round((host & 255) * shade));
        int g = Math.min(255, (int) Math.round((host >>> 8 & 255) * shade));
        int b = Math.min(255, (int) Math.round((host >>> 16 & 255) * shade));
        return mask & 0xFF000000 | b << 16 | g << 8 | r;
    }

    public static int oreColor(int width, int height, IntBinaryOperator ore,
                               int hostWidth, int hostHeight, IntBinaryOperator host) {
        long red = 0, green = 0, blue = 0, weight = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = ore.applyAsInt(x, y);
                if ((pixel >>> 24) < 128) continue;
                int background = host.applyAsInt(x * hostWidth / width, y * hostHeight / height);
                int r = pixel & 255, g = pixel >>> 8 & 255, b = pixel >>> 16 & 255;
                int dr = r - (background & 255), dg = g - (background >>> 8 & 255), db = b - (background >>> 16 & 255);
                int contrast = dr * dr + dg * dg + db * db;
                // Ignore the host itself, including minor texture/compression differences.
                if (contrast < 40 * 40) continue;
                red += (long) r * contrast;
                green += (long) g * contrast;
                blue += (long) b * contrast;
                weight += contrast;
            }
        }
        return weight == 0 ? -1 : (int) (red / weight) << 16 | (int) (green / weight) << 8 | (int) (blue / weight);
    }
}

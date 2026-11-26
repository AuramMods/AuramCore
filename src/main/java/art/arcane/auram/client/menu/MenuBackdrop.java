package art.arcane.auram.client.menu;

import art.arcane.auram.Auram;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

final class MenuBackdrop {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Auram.MODID, "menu_backdrop");
    private static final int COLUMNS = 96;
    private static final int[] BLUR = {1, 4, 7, 10, 12, 10, 7, 4, 1};
    private static final int WEIGHT = 56;
    private static final long RETRY_NANOS = 300_000_000L;

    private MapMenuView view;
    private DynamicTexture texture;
    private int width;
    private int height;
    private long retryAfter;

    MapMenuView view() {
        return view;
    }

    void prepare(Minecraft minecraft, boolean mapAvailable, double x, double z, int width, int height, long now) {
        if (!mapAvailable || width <= 0 || height <= 0) {
            close(minecraft);
            return;
        }
        if (width != this.width || height != this.height) {
            close(minecraft);
            this.width = width;
            this.height = height;
        }
        if (view != null || now < retryAfter) return;
        retryAfter = now + RETRY_NANOS;

        int rows = Mth.clamp(Math.round((float) COLUMNS * height / width), 24, COLUMNS);
        int[] pixels = new int[COLUMNS * rows];
        var framed = FtbMapMenu.snapshot(minecraft.player, x, z, width, height, pixels, COLUMNS, rows);
        if (framed == null) return;
        var scratch = new int[pixels.length];
        blur(pixels, scratch, COLUMNS, rows, true);
        blur(scratch, pixels, COLUMNS, rows, false);

        texture = new DynamicTexture(COLUMNS, rows, false);
        minecraft.getTextureManager().register(TEXTURE, texture);
        texture.setFilter(true, false);
        var image = texture.getPixels();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                image.setPixelRGBA(column, row, pixels[row * COLUMNS + column]);
            }
        }
        texture.upload();
        view = framed;
    }

    void render(GuiGraphics graphics, int width, int height, float opacity) {
        if (opacity <= 0) return;
        if (texture == null) {
            int alpha = Math.round(opacity * 255) << 24;
            graphics.fill(0, 0, width, height, alpha | 0x101722);
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(0.45F, 0.49F, 0.56F, opacity);
        var image = texture.getPixels();
        graphics.blit(TEXTURE, 0, 0, width, height, 0, 0, image.getWidth(), image.getHeight(),
                image.getWidth(), image.getHeight());
        graphics.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    void close(Minecraft minecraft) {
        if (texture != null) {
            minecraft.getTextureManager().release(TEXTURE);
            texture = null;
        }
        view = null;
        retryAfter = 0;
    }

    private static void blur(int[] source, int[] target, int width, int height, boolean horizontal) {
        int radius = BLUR.length / 2;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = 0, green = 0, blue = 0;
                for (int offset = -radius; offset <= radius; offset++) {
                    int column = horizontal ? Mth.clamp(x + offset, 0, width - 1) : x;
                    int row = horizontal ? y : Mth.clamp(y + offset, 0, height - 1);
                    int color = source[row * width + column];
                    int weight = BLUR[offset + radius];
                    red += (color & 255) * weight;
                    green += (color >>> 8 & 255) * weight;
                    blue += (color >>> 16 & 255) * weight;
                }
                target[y * width + x] = 0xff000000 | (blue / WEIGHT << 16) | (green / WEIGHT << 8) | red / WEIGHT;
            }
        }
    }
}

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
    // Approximate the area where a radius-one disk overlaps each of the nine texels.
    private static final int[][] DISK_WEIGHTS = {
            {5, 29, 5},
            {29, 64, 29},
            {5, 29, 5}
    };
    private static final int DISK_WEIGHT = 200;
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

        var snapshot = FtbMapMenu.snapshot(minecraft.player, x, z, width, height);
        if (snapshot == null) return;

        int columns = snapshot.columns();
        int rows = snapshot.rows();
        texture = new DynamicTexture(columns, rows, false);
        minecraft.getTextureManager().register(TEXTURE, texture);
        var image = texture.getPixels();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                image.setPixelRGBA(column, row, blurPixel(snapshot.pixels(), columns, rows, column, row));
            }
        }
        texture.upload();
        texture.setFilter(true, false);
        view = snapshot.view();
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
        graphics.setColor(0.42F, 0.46F, 0.53F, opacity);
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

    private static int blurPixel(int[] source, int width, int height, int x, int y) {
        int red = 0, green = 0, blue = 0;
        for (int dy = -1; dy <= 1; dy++) {
            int row = Mth.clamp(y + dy, 0, height - 1) * width;
            for (int dx = -1; dx <= 1; dx++) {
                int color = source[row + Mth.clamp(x + dx, 0, width - 1)];
                int weight = DISK_WEIGHTS[dy + 1][dx + 1];
                red += (color & 255) * weight;
                green += (color >>> 8 & 255) * weight;
                blue += (color >>> 16 & 255) * weight;
            }
        }
        return 0xff000000 | (blue / DISK_WEIGHT << 16) | (green / DISK_WEIGHT << 8) | red / DISK_WEIGHT;
    }
}

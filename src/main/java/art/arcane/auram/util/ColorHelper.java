package art.arcane.auram.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.awt.*;

public class ColorHelper {

    public static int computeColorFor(Item item) {
        if (item == null) return -1;

        try {
            ItemStack stack = new ItemStack(item);
            var model = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);
            TextureAtlasSprite sprite = model.getParticleIcon();
            if (sprite == null || sprite.contents() == null) return -1;
            int width = sprite.contents().width();
            int height = sprite.contents().height();
            long r = 0, g = 0, b = 0;
            int count = 0;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    // Get pixel color (ABGR format usually)
                    int pixel = sprite.getPixelRGBA(0, x, y);

                    // Check alpha (transparency)
                    int alpha = (pixel >> 24) & 0xFF;
                    if (alpha < 20) continue; // Skip transparent pixels

                    // Extract RGB
                    int blue = (pixel >> 16) & 0xFF;
                    int green = (pixel >> 8) & 0xFF;
                    int red = (pixel) & 0xFF;

                    r += red;
                    g += green;
                    b += blue;
                    count++;
                }
            }

            if (count == 0) return -1;

            return new Color((int)(r/count), (int)(g/count), (int)(b/count)).getRGB();

        } catch (Exception e) {
            return -1;
        }
    }
    public static int getDominantColor(Block block, RockHost host) {
        BlockState state = block.defaultBlockState();
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        TextureAtlasSprite sprite = model.getParticleIcon();

        if (sprite == null) return -1;
        TextureAtlasSprite background = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(ResourceLocation.parse("minecraft:block/" + host.texture));
        int color = RockPixels.oreColor(sprite.contents().width(), sprite.contents().height(),
                (x, y) -> sprite.getPixelRGBA(0, x, y), background.contents().width(), background.contents().height(),
                (x, y) -> background.getPixelRGBA(0, x, y));
        if (color != -1) return color;

        // Custom ore textures may have no distinct host; retain the original
        // saturation-weighted fallback rather than losing their tint entirely.
        long totalRed = 0;
        long totalGreen = 0;
        long totalBlue = 0;
        long totalWeight = 0;

        for (int x = 0; x < sprite.contents().width(); x++) {
            for (int y = 0; y < sprite.contents().height(); y++) {
                int rgba = sprite.getPixelRGBA(0, x, y);

                int a = (rgba >> 24) & 0xFF;
                int b = (rgba >> 16) & 0xFF;
                int g = (rgba >> 8) & 0xFF;
                int r = (rgba) & 0xFF;

                if (a < 255) continue;
                float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
                float saturation = hsb[1]; // 0.0 (gray) to 1.0 (super bright)

                int weight = (int) (saturation * saturation * 100);
                if (weight <= 1) weight = 1;

                totalRed += r * weight;
                totalGreen += g * weight;
                totalBlue += b * weight;
                totalWeight += weight;
            }
        }

        if (totalWeight == 0) return -1;

        int avgR = (int) (totalRed / totalWeight);
        int avgG = (int) (totalGreen / totalWeight);
        int avgB = (int) (totalBlue / totalWeight);

        return (avgR << 16) | (avgG << 8) | avgB;

    }
}

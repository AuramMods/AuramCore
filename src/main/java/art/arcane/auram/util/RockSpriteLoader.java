package art.arcane.auram.util;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.client.textures.ForgeTextureMetadata;
import net.minecraftforge.client.textures.ITextureAtlasSpriteLoader;

import java.io.IOException;
import java.io.InputStream;

/** Reuses the rock silhouette with actual host-stone grain on every asset reload. */
public final class RockSpriteLoader implements ITextureAtlasSpriteLoader {
    @Override
    public SpriteContents loadContents(ResourceLocation name, Resource resource, FrameSize frameSize,
                                       NativeImage image, AnimationMetadataSection animation,
                                       ForgeTextureMetadata metadata) {
        for (RockHost host : RockHost.values()) {
            if (host == RockHost.STONE || !name.getPath().equals("item/" + host.model)) continue;
            ResourceLocation texture = ResourceLocation.parse("minecraft:textures/block/" + host.texture + ".png");
            try (InputStream stream = Minecraft.getInstance().getResourceManager().open(texture);
                 NativeImage stone = NativeImage.read(stream)) {
                // Use the first square tile if the host texture is an animation strip.
                int tileHeight = Math.min(stone.getWidth(), stone.getHeight());
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int pixel = stone.getPixelRGBA(x % frameSize.width() * stone.getWidth() / frameSize.width(),
                                y % frameSize.height() * tileHeight / frameSize.height());
                        image.setPixelRGBA(x, y, RockPixels.hostPixel(image.getPixelRGBA(x, y), pixel));
                    }
                }
            } catch (IOException e) {
                LogUtils.getLogger().warn("Cannot generate {} from {}; retaining the stone rock", name, texture, e);
            }
            break;
        }
        return new SpriteContents(name, frameSize, image, animation, metadata);
    }

    @Override
    public TextureAtlasSprite makeSprite(ResourceLocation atlas, SpriteContents contents, int width, int height,
                                          int x, int y, int mipmapLevel) {
        return new TextureAtlasSprite(atlas, contents, width, height, x, y) { };
    }
}

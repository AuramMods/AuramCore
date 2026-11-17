package art.arcane.auram.smoke;

import art.arcane.auram.Auram;
import art.arcane.auram.util.RockHost;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;

/** Real atlas/model validation at the menu, including a second resource reload. */
@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class RockClientSmoke {
    private static volatile int phase;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("auram.smoke.rocks") || event.phase != TickEvent.Phase.END
                || phase == 1 || phase == 3) return;
        Minecraft client = Minecraft.getInstance();
        if (client.getOverlay() != null || client.screen == null) return;
        try {
            check(client);
            if (phase == 0) {
                phase = 1;
                client.reloadResourcePacks().whenComplete((result, error) -> {
                    if (error != null) fail(client, error);
                    else phase = 2;
                });
            } else {
                Files.writeString(Path.of("auram-rock-client-pass.txt"), "PASS stitched host sprites, vanilla rock models, ore tints and resource reload\n");
                System.out.println("QA_EVT {\"event\":\"rock-client\",\"status\":\"pass\",\"details\":\"Sprites, item models, ore colors and reload verified\"}");
                phase = 3;
                client.stop();
            }
        } catch (Throwable error) {
            fail(client, error);
        }
    }

    private static void check(Minecraft client) throws Exception {
        TextureAtlasSprite mask = sprite(client, "auram:item/rock");
        double stoneBrightness = brightness(mask.contents().getOriginalImage());
        for (RockHost host : RockHost.values()) {
            if (host == RockHost.STONE) continue;
            TextureAtlasSprite sprite = sprite(client, "auram:item/" + host.model);
            NativeImage image = sprite.contents().getOriginalImage();
            require(image.getWidth() == 16 && image.getHeight() == 16, "Host sprite dimensions: " + host);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                require((image.getPixelRGBA(x, y) >>> 24) == (mask.getPixelRGBA(0, x, y) >>> 24), "Silhouette: " + host);
            }
            if (host == RockHost.END_STONE) require(brightness(image) > stoneBrightness * 1.5, "End stone should be pale");
            if (host == RockHost.DEEPSLATE) require(brightness(image) < stoneBrightness, "Deepslate should be darker");
            if (host == RockHost.NETHERRACK) {
                int pixel = image.getPixelRGBA(7, 7);
                require((pixel & 255) > (pixel >>> 8 & 255), "Netherrack should be red");
            }
            image.writeToFile(Path.of("rock-" + host.texture + ".png"));
        }
        checkModel(client, "iron_ore", RockHost.STONE);
        checkModel(client, "deepslate_diamond_ore", RockHost.DEEPSLATE);
        checkModel(client, "nether_quartz_ore", RockHost.NETHERRACK);
        checkModel(client, "nether_gold_ore", RockHost.NETHERRACK);
        ItemStack quartz = rock("nether_quartz_ore");
        int color = client.getItemColors().getColor(quartz, 1);
        require((color >>> 16 & 255) > 150 && (color >>> 8 & 255) > 150, "Quartz must retain a pale ore tint");
    }

    private static void checkModel(Minecraft client, String ore, RockHost host) {
        var model = client.getItemRenderer().getModel(rock(ore), null, null, 0);
        String expected = "auram:item/" + (host == RockHost.STONE ? "rock" : host.model);
        require(model.getQuads(null, null, RandomSource.create(1)).stream()
                .anyMatch(quad -> quad.getSprite().contents().name().toString().equals(expected)), "Host model: " + ore);
    }

    private static ItemStack rock(String ore) {
        ResourceLocation rockId = Auram.ORE_BLOCK_ID_TO_ROCK_ID.get(ResourceLocation.parse("minecraft:" + ore));
        return new ItemStack(ForgeRegistries.ITEMS.getValue(rockId));
    }

    private static TextureAtlasSprite sprite(Minecraft client, String name) {
        TextureAtlasSprite sprite = client.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.parse(name));
        require(sprite.contents().name().toString().equals(name), "Missing sprite: " + name);
        return sprite;
    }

    private static double brightness(NativeImage image) {
        long sum = 0, count = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int p = image.getPixelRGBA(x, y);
            if ((p >>> 24) == 0) continue;
            sum += (p & 255) + (p >>> 8 & 255) + (p >>> 16 & 255);
            count += 3;
        }
        return (double) sum / count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void fail(Minecraft client, Throwable error) {
        phase = 3;
        error.printStackTrace();
        try { Files.writeString(Path.of("auram-rock-client-failed.txt"), error.toString()); }
        catch (Exception writeError) { writeError.printStackTrace(); }
        client.stop();
    }
}

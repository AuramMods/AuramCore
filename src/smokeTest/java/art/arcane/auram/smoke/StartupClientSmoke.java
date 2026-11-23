package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Isolated hidden production-client startup and resource reload validation. */
@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class StartupClientSmoke {
    private static int ticks, phase;
    private static final List<Map<String, Object>> checks = new ArrayList<>();

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!System.getProperty("auram.smoke", "").equals("startup") || event.phase != TickEvent.Phase.END || phase == 1 || phase == 3) return;
        Minecraft client = Minecraft.getInstance();
        if (client.getOverlay() != null || !(client.screen instanceof TitleScreen) || ++ticks < 15) return;
        try {
            checks.add(check(client));
            if (phase == 0) {
                phase = 1;
                client.reloadResourcePacks().whenComplete((unused, error) -> {
                    if (error != null) fail(client, error); else { ticks = 0; phase = 2; }
                });
            } else {
                try (NativeImage screenshot = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    screenshot.writeToFile(Path.of("startup-menu.png"));
                }
                Files.writeString(Path.of("startup-client-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(checks) + "\n");
                Files.writeString(Path.of("auram-startup-client-pass.txt"), "PASS mod construction, handler maps, assets, stitched sprites and resource reload\n");
                System.out.println("QA_EVT {\"event\":\"startup-client\",\"status\":\"pass\",\"details\":\"Quark/Supplementaries, mod resources and reload verified\"}");
                phase = 3; client.stop();
            }
        } catch (Throwable error) { fail(client, error); }
    }

    private static Map<String, Object> check(Minecraft client) throws Exception {
        for (String id : List.of("quark", "supplementaries")) {
            require(ModList.get().getModContainerById(id).orElseThrow().getMod() != null, id + " constructed");
        }
        Object zeta = Class.forName("org.violetmoon.quark.base.Quark").getField("ZETA").get(null);
        Object bus = zeta.getClass().getField("playBus").get(zeta);
        var field = Class.forName("org.violetmoon.zetaimplforge.event.ForgeZetaEventBus").getDeclaredField("convertedHandlers");
        field.setAccessible(true);
        Object map = field.get(bus);
        require(map.getClass().getName().contains("SynchronizedMap"), "actual Quark handler registry is synchronized");
        List<String> packs = client.getResourceManager().listPacks().map(p -> p.packId()).toList();
        require(packs.contains("mod_resources"), "Forge mod resource pack loaded");
        List<String> textures = List.of("iceandfire:textures/gui/main_menu/table.png", "distanthorizons:textures/gui/button.png",
                "quark:textures/block/limestone.png", "supplementaries:textures/block/blackstone_tiles.png");
        for (String path : textures) {
            try (var stream = client.getResourceManager().open(ResourceLocation.parse(path)); NativeImage image = NativeImage.read(stream)) {
                require(image.getWidth() > 0 && image.getHeight() > 0, "decoded texture " + path);
            }
        }
        for (String path : List.of("quark:block/limestone", "auram:item/rock", "auram:item/rock_netherrack")) {
            var sprite = client.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.parse(path));
            require(sprite.contents().name().toString().equals(path), "stitched sprite " + path);
        }
        var result = new LinkedHashMap<String, Object>(Map.of(
                "phase", phase, "mods", ModList.get().size(), "packs", packs, "textures", textures,
                "map_class", map.getClass().getName(), "sprites_present", true, "scheduling", SchedulingSmoke.capture()));
        if (Boolean.getBoolean("auram.smoke.tooltipLayout")) {
            result.put("tooltip_layout", TooltipLayoutSmoke.run(client));
        }
        return result;
    }

    private static void fail(Minecraft client, Throwable error) {
        error.printStackTrace();
        try { Files.writeString(Path.of("auram-startup-client-failed.txt"), error.toString()); } catch (Exception ignored) { }
        System.out.println("QA_EVT {\"event\":\"startup-client\",\"status\":\"fail\",\"details\":\"See failure marker and log\"}");
        phase = 3; client.stop();
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

package art.arcane.auram.smoke;

import ca.weblite.objc.Client;
import ca.weblite.objc.Proxy;
import com.google.gson.GsonBuilder;
import com.sun.jna.Pointer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFWNativeCocoa;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Reads actual Cocoa window/icon state, then repeats after a resource reload. */
@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class BrandingClientSmoke {
    private static int phase, ticks;
    private static final List<Map<String, Object>> results = new ArrayList<>();

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!System.getProperty("auram.smoke", "").equals("branding") || event.phase != TickEvent.Phase.END || phase == 1 || phase == 3) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getOverlay() != null || !(minecraft.screen instanceof TitleScreen) || ++ticks < 15) return;
        try {
            minecraft.updateTitle();
            Client objc = Client.getInstance();
            Proxy window = new Proxy(new Pointer(GLFWNativeCocoa.glfwGetCocoaWindow(minecraft.getWindow().getWindow())));
            String title = window.sendString("title");
            require(title.equals("Auram"), "native window title: " + title);
            if (Boolean.getBoolean("auram.smoke.expectBootstrap")) {
                require("Auram".equals(System.getProperty("auram.branding.createdTitle")), "first GLFW creation branded");
                minecraft.getWindow().setTitle("Minecraft reset attempt");
                require(window.sendString("title").equals("Auram"), "subsequent GLFW title assignment branded");
            }
            Proxy app = objc.sendProxy("NSApplication", "sharedApplication");
            Proxy tiff = app.sendProxy("applicationIconImage").sendProxy("TIFFRepresentation");
            byte[] pixels = tiff.sendPointer("bytes").getByteArray(0, tiff.sendInt("length"));
            var actual = ImageIO.read(new ByteArrayInputStream(pixels));
            // Convert Cocoa's profiled TIFF to sRGB before exporting the evidence PNG.
            var exported = new java.awt.image.BufferedImage(actual.getWidth(), actual.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            exported.setRGB(0, 0, actual.getWidth(), actual.getHeight(), actual.getRGB(0, 0, actual.getWidth(), actual.getHeight(), null, 0, actual.getWidth()), 0, actual.getWidth());
            ImageIO.write(exported, "PNG", Path.of("native-icon-" + phase + ".png").toFile());
            try (var stream = art.arcane.auram.client.ClientBranding.class.getResourceAsStream("/assets/auram/branding/icon.png")) {
                var expected = ImageIO.read(stream);
                require(actual.getWidth() == expected.getWidth() && actual.getHeight() == expected.getHeight(), "native icon dimensions");
                int differing = 0;
                for (int y = 0; y < actual.getHeight(); y++) for (int x = 0; x < actual.getWidth(); x++) {
                    int a = actual.getRGB(x, y), b = expected.getRGB(x, y);
                    for (int shift : new int[]{0, 8, 16, 24}) {
                        // Cocoa's TIFF round trip premultiplies translucent pixels.
                        // Compare their displayed contribution, retaining strict alpha/opaque checks.
                        int ac = (a >>> shift) & 255, bc = (b >>> shift) & 255;
                        if (shift != 24) { ac = (ac * (a >>> 24) + 127) / 255; bc = (bc * (b >>> 24) + 127) / 255; }
                        if (Math.abs(ac - bc) > 2) { differing++; break; }
                    }
                }
                require(differing == 0, "native unicorn icon pixels differ: " + differing);
            }
            results.add(Map.of("phase", phase, "native_title", title, "native_icon_pixels_match", true,
                    "bootstrap_created_title", System.getProperty("auram.branding.createdTitle", "agent absent"),
                    "process_name", objc.sendProxy("NSProcessInfo", "processInfo").sendString("processName")));
            if (phase == 0) {
                phase = 1;
                minecraft.reloadResourcePacks().whenComplete((unused, error) -> {
                    if (error != null) fail(minecraft, error); else { phase = 2; ticks = 0; }
                });
            } else {
                Files.writeString(Path.of("branding-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(results) + "\n");
                Files.writeString(Path.of("auram-branding-pass.txt"), "PASS native title/icon and resource reload\n");
                phase = 3; minecraft.stop();
            }
        } catch (Throwable error) { fail(minecraft, error); }
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void fail(Minecraft client, Throwable error) {
        error.printStackTrace();
        try { Files.writeString(Path.of("auram-branding-failed.txt"), error.toString()); } catch (Exception ignored) { }
        phase = 3; client.stop();
    }
}

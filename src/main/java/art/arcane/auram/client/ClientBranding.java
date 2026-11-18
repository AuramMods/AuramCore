package art.arcane.auram.client;

import com.mojang.blaze3d.platform.MacosUtil;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Available during Window construction, before the resource manager exists. */
public final class ClientBranding {
    private ClientBranding() { }

    public static String name() { return System.getProperty("auram.branding.name", "Auram"); }

    private static InputStream icon() throws IOException {
        String external = System.getProperty("auram.branding.icon");
        if (external != null) return Files.newInputStream(Path.of(external));
        InputStream stream = ClientBranding.class.getResourceAsStream("/assets/auram/branding/icon.png");
        if (stream == null) throw new IOException("Missing bundled Auram application icon");
        return stream;
    }

    public static void setIcon(long window) throws IOException {
        if (Minecraft.ON_OSX) {
            // Vanilla uses this same Cocoa API for its ICNS; NSImage also accepts PNG.
            MacosUtil.loadIcon(ClientBranding::icon);
        } else {
            try (InputStream stream = icon(); NativeImage image = NativeImage.read(stream);
                 MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer pixels = MemoryUtil.memAlloc(image.getWidth() * image.getHeight() * 4);
                try {
                    pixels.asIntBuffer().put(image.getPixelsRGBA());
                    GLFWImage.Buffer icons = GLFWImage.malloc(1, stack);
                    icons.width(image.getWidth()).height(image.getHeight()).pixels(pixels);
                    GLFW.glfwSetWindowIcon(window, icons);
                } finally { MemoryUtil.memFree(pixels); }
            }
        }
    }
}

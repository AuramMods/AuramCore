package art.arcane.auram.client;

import art.arcane.auram.Auram;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MinimapWidgetCache {
    private static final long DRAW_PERIOD = 33_333_334L;
    private static ShaderInstance shader;
    private static boolean capturingAlpha;
    private TextureTarget target;
    private int top;
    private int bottom;
    private final int[] previousViewport = new int[4];
    private int previousDraw;
    private int previousRead;
    private boolean capturing;
    private long lastDraw;
    private boolean valid;
    private Layout layout;

    @SubscribeEvent
    public static void registerShader(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation(Auram.MODID, "minimap_composite"), DefaultVertexFormat.POSITION_TEX),
                loaded -> shader = loaded);
    }

    public static boolean ready() {
        return shader != null;
    }

    public static boolean capturingAlpha() {
        return capturingAlpha;
    }

    public void setLayout(Layout next) {
        if (!next.equals(layout)) valid = false;
        layout = next;
    }

    public boolean canDraw(long now) {
        return lastDraw == 0 || now - lastDraw >= DRAW_PERIOD;
    }

    public void begin(GuiGraphics graphics, long now, int minY, int maxY) {
        graphics.flush();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        previousRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        previousDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int height = layout.windowHeight();
        int width = layout.windowWidth();
        double pixelsPerY = (double) height / layout.guiHeight();
        top = Math.max(0, (int) Math.floor(minY * pixelsPerY));
        bottom = Math.min(height, (int) Math.ceil(maxY * pixelsPerY));
        if (bottom <= top) {
            top = 0;
            bottom = height;
        }
        int bandHeight = bottom - top;
        capturing = true;
        valid = false;
        lastDraw = now;
        if (target == null || target.width != width || target.height != bandHeight) {
            if (target != null) target.destroyBuffers();
            target = new TextureTarget(width, bandHeight, true, Minecraft.ON_OSX);
            target.setClearColor(0, 0, 0, 0);
        }
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(false);
        // Keep FTB's screen-space coordinates and crop the framebuffer vertically.
        RenderSystem.viewport(0, bottom - height, width, height);
        capturingAlpha = true;
        RenderSystem.defaultBlendFunc();
    }

    public void finish(GuiGraphics graphics) {
        if (!capturing) return;
        try {
            graphics.flush();
            valid = true;
        } finally {
            restore();
        }
        replay(graphics);
    }

    public void abort() {
        if (capturing) restore();
    }

    private void restore() {
        capturingAlpha = false;
        capturing = false;
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        RenderSystem.viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
        RenderSystem.defaultBlendFunc();
    }

    public void replay(GuiGraphics graphics) {
        if (!valid) return;
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.blendFuncSeparate(GL14.GL_ONE, GL14.GL_ONE_MINUS_SRC_ALPHA,
                GL14.GL_ONE, GL14.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        var pose = graphics.pose().last().pose();
        float x = layout.guiWidth();
        float guiUnitsPerPixel = (float) layout.guiHeight() / layout.windowHeight();
        float y0 = top * guiUnitsPerPixel;
        float y1 = bottom * guiUnitsPerPixel;
        var tesselator = Tesselator.getInstance();
        var buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(pose, 0, y0, 0).uv(0, 1).endVertex();
        buffer.vertex(pose, 0, y1, 0).uv(0, 0).endVertex();
        buffer.vertex(pose, x, y1, 0).uv(1, 0).endVertex();
        buffer.vertex(pose, x, y0, 0).uv(1, 1).endVertex();
        tesselator.end();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    public void invalidate() {
        valid = false;
    }

    public record Layout(int x, int y, int size, int windowWidth, int windowHeight,
                         int guiWidth, int guiHeight, boolean square, boolean rotationLocked, int visibility) {}
}

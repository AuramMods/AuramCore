package art.arcane.auram.client.menu;

import art.arcane.auram.Auram;
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

import java.io.IOException;

@Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MenuGlass {
    private static ShaderInstance shader;
    private final MenuFrame strip = new MenuFrame();

    @SubscribeEvent
    public static void registerShader(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation(Auram.MODID, "menu_glass"), DefaultVertexFormat.POSITION_TEX),
                loaded -> shader = loaded);
    }

    void drawTop(Minecraft client, GuiGraphics graphics, int width, int height, int barHeight) {
        var main = client.getMainRenderTarget();
        if (shader == null || main.viewWidth < 1 || height <= barHeight) {
            graphics.fill(0, 0, width, barHeight, 0xf31b2634);
            return;
        }
        graphics.flush();
        int barPixels = Math.max(1, Math.round((float) main.viewHeight * barHeight / height));
        int sourceHeight = Math.min(main.viewHeight, barPixels * 2);
        strip.capture(client, main.viewWidth, 0, sourceHeight, Math.max(1, main.viewWidth / 8), Math.max(1, sourceHeight / 8));
        drawFrame(graphics, strip.texture(), width, barHeight, 1, 1.0F - (float) barPixels / sourceHeight, 1, 1);
    }

    void drawRail(Minecraft client, GuiGraphics graphics, int width, int height, int top, int railWidth, int expandedWidth) {
        var main = client.getMainRenderTarget();
        int sourceTop = Math.round((float) main.viewHeight * top / height);
        int sourceHeight = main.viewHeight - sourceTop;
        if (shader == null || main.viewWidth < 1 || sourceHeight < 1 || width <= 0) {
            graphics.fill(0, top, railWidth, height, 0xf31b2634);
            return;
        }
        graphics.flush();
        float scale = (float) main.viewWidth / width;
        int sourceWidth = Math.min(main.viewWidth, Math.max(1, (int) Math.ceil(scale * expandedWidth) + 16));
        strip.capture(client, sourceWidth, sourceTop, sourceHeight, Math.max(1, sourceWidth / 8), Math.max(1, sourceHeight / 8));
        graphics.pose().pushPose();
        graphics.pose().translate(0, top, 0);
        drawFrame(graphics, strip.texture(), railWidth, height - top, scale * railWidth / sourceWidth, 0, 1, 1);
        graphics.pose().popPose();
    }

    void close() {
        strip.close();
    }

    static boolean ready() {
        return shader != null;
    }

    static void drawFrame(GuiGraphics graphics, int texture, int width, int height, float rightU, float bottomV,
                          float frost, float opacity) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        shader.safeGetUniform("Frost").set(frost);
        shader.safeGetUniform("Opacity").set(opacity);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, texture);
        var pose = graphics.pose().last().pose();
        var tessellator = Tesselator.getInstance();
        var buffer = tessellator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(pose, 0, 0, 0).uv(0, 1).endVertex();
        buffer.vertex(pose, 0, height, 0).uv(0, bottomV).endVertex();
        buffer.vertex(pose, width, height, 0).uv(rightU, bottomV).endVertex();
        buffer.vertex(pose, width, 0, 0).uv(rightU, 1).endVertex();
        tessellator.end();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }
}

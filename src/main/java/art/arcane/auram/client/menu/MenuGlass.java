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

    void draw(Minecraft client, GuiGraphics graphics, int width, int height, int railWidth, int expandedWidth) {
        var main = client.getMainRenderTarget();
        if (shader == null || main.viewWidth < 1 || main.viewHeight < 1 || width <= 0) {
            graphics.fill(0, 0, railWidth, height, 0xf31b2634);
            return;
        }
        graphics.flush();
        float railPixels = (float) main.viewWidth * railWidth / width;
        // Include the pixels just beyond the sidebar so its blur can sample across the edge.
        int sourceWidth = Math.min(main.viewWidth, Math.max(1, (int) Math.ceil((float) main.viewWidth * expandedWidth / width) + 16));
        strip.capture(client, sourceWidth, main.viewHeight, Math.max(1, sourceWidth / 8), Math.max(1, main.viewHeight / 8));
        drawFrame(graphics, strip.texture(), railWidth, height, railPixels / sourceWidth, 1, 1);
    }

    static boolean ready() {
        return shader != null;
    }

    static void drawFrame(GuiGraphics graphics, int texture, int width, int height, float rightU,
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
        buffer.vertex(pose, 0, height, 0).uv(0, 0).endVertex();
        buffer.vertex(pose, width, height, 0).uv(rightU, 0).endVertex();
        buffer.vertex(pose, width, 0, 0).uv(rightU, 1).endVertex();
        tessellator.end();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }
}

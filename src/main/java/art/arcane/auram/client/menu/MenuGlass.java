package art.arcane.auram.client.menu;

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
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL30;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MenuGlass {
    private static ShaderInstance shader;
    private TextureTarget strip;

    @SubscribeEvent
    public static void registerShader(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation(Auram.MODID, "menu_glass"), DefaultVertexFormat.POSITION_TEX),
                loaded -> shader = loaded);
    }

    void draw(Minecraft client, GuiGraphics graphics, int width, int height, int contentTop, int barHeight) {
        var main = client.getMainRenderTarget();
        if (shader == null || main.viewWidth < 1 || height <= barHeight) {
            graphics.fill(0, 0, width, barHeight, 0xf31b2634);
            return;
        }
        graphics.flush();
        int stripeHeight = Math.max(1, Math.round((float) main.viewHeight * barHeight / height));
        int top = Mth.clamp(main.viewHeight - Math.round((float) main.viewHeight * contentTop / height),
                stripeHeight, main.viewHeight);
        int columns = Math.max(1, main.viewWidth / 8);
        int rows = Math.max(1, stripeHeight / 8);
        int originalRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int originalDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            if (strip == null || strip.width != columns || strip.height != rows) {
                if (strip != null) strip.destroyBuffers();
                strip = new TextureTarget(columns, rows, false, Minecraft.ON_OSX);
                strip.setFilterMode(GL30.GL_LINEAR);
                RenderSystem.viewport(0, 0, main.viewWidth, main.viewHeight);
            }
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, originalDraw);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, strip.frameBufferId);
            GL30.glBlitFramebuffer(0, top - stripeHeight, main.viewWidth, top, 0, 0, columns, rows,
                    GL30.GL_COLOR_BUFFER_BIT, GL30.GL_LINEAR);
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, originalRead);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, originalDraw);
        }

        RenderSystem.disableDepthTest();
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, strip.getColorTextureId());
        var pose = graphics.pose().last().pose();
        var tessellator = Tesselator.getInstance();
        var buffer = tessellator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(pose, 0, 0, 0).uv(0, 1).endVertex();
        buffer.vertex(pose, 0, barHeight, 0).uv(0, 0).endVertex();
        buffer.vertex(pose, width, barHeight, 0).uv(1, 0).endVertex();
        buffer.vertex(pose, width, 0, 0).uv(1, 1).endVertex();
        tessellator.end();
        RenderSystem.enableDepthTest();
    }
}

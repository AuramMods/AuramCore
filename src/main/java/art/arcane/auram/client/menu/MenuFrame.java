package art.arcane.auram.client.menu;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL30;

final class MenuFrame {
    private TextureTarget target;

    void capture(Minecraft client, int sourceWidth, int sourceTop, int sourceHeight, int width, int height) {
        var main = client.getMainRenderTarget();
        int originalRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int originalDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            if (target == null || target.width != width || target.height != height) {
                if (target != null) target.destroyBuffers();
                target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
                target.setFilterMode(GL30.GL_LINEAR);
                RenderSystem.viewport(0, 0, main.viewWidth, main.viewHeight);
            }
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
            int top = main.viewHeight - sourceTop;
            GL30.glBlitFramebuffer(0, top - sourceHeight, sourceWidth, top,
                    0, 0, width, height, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_LINEAR);
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, originalRead);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, originalDraw);
        }
    }

    void close() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }

    int texture() {
        return target.getColorTextureId();
    }
}

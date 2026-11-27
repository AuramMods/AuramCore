package art.arcane.auram.mixin.menu;

import art.arcane.auram.client.menu.AdvancementCanvas;
import art.arcane.auram.client.menu.AuramAdvancementsScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(AdvancementTab.class)
abstract class AdvancementTabCanvasMixin implements AdvancementCanvas {
    @Shadow @Final private AdvancementsScreen screen;
    @Shadow @Final private DisplayInfo display;
    @Shadow @Final private AdvancementWidget root;
    @Shadow private int minX;
    @Shadow private int minY;
    @Shadow private int maxX;
    @Shadow private int maxY;
    @Shadow private double scrollX;
    @Shadow private double scrollY;
    @Shadow private boolean centered;

    @Override
    public void auram$drawCanvas(GuiGraphics graphics) {
        if (!centered) {
            scrollX = (screen.width - minX - maxX) * 0.5;
            scrollY = (screen.height - minY - maxY) * 0.5;
            centered = true;
        }
        int x = Mth.floor(scrollX);
        int y = Mth.floor(scrollY);
        var background = display.getBackground();
        graphics.flush();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, background == null ? TextureManager.INTENTIONAL_MISSING_TEXTURE : background);
        var tessellator = Tesselator.getInstance();
        var buffer = tessellator.getBuilder();
        var pose = graphics.pose().last().pose();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        // Submit all background tiles in one draw. Custom tile textures may use clamp-to-edge.
        for (int top = Math.floorMod(y, 16) - 16; top < screen.height; top += 16) {
            for (int left = Math.floorMod(x, 16) - 16; left < screen.width; left += 16) {
                buffer.vertex(pose, left, top, 0).uv(0, 0).endVertex();
                buffer.vertex(pose, left, top + 16, 0).uv(0, 1).endVertex();
                buffer.vertex(pose, left + 16, top + 16, 0).uv(1, 1).endVertex();
                buffer.vertex(pose, left + 16, top, 0).uv(1, 0).endVertex();
            }
        }
        tessellator.end();
        root.drawConnectivity(graphics, x, y, true);
        root.drawConnectivity(graphics, x, y, false);
        root.draw(graphics, x, y);
    }

    @Override
    public void auram$pan(double x, double y) {
        // Keep the tree reachable even when it's smaller than the canvas.
        scrollX = Mth.clamp(scrollX + x, screen.width * 0.5 - maxX, screen.width * 0.5 - minX);
        scrollY = Mth.clamp(scrollY + y, screen.height * 0.5 - maxY, screen.height * 0.5 - minY);
    }

    @ModifyConstant(method = "drawTooltips", constant = @Constant(intValue = 234))
    private int auram$canvasTooltipWidth(int vanilla) {
        return screen instanceof AuramAdvancementsScreen ? screen.width : vanilla;
    }

    @ModifyConstant(method = "drawTooltips", constant = @Constant(intValue = 113))
    private int auram$canvasTooltipHeight(int vanilla) {
        return screen instanceof AuramAdvancementsScreen ? screen.height : vanilla;
    }
}

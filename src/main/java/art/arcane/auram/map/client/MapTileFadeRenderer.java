package art.arcane.auram.map.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public final class MapTileFadeRenderer {
    private static final int[] ALPHAS = new int[MapTileFades.CHUNK_COUNT];

    private MapTileFadeRenderer() {}

    public static boolean draw(MapTileFades fades, GuiGraphics graphics, int x, int y, int width, int height) {
        if (!fades.fillAlphas(ALPHAS, System.nanoTime())) return false;
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);

        // Batch neighboring chunks with the same opacity. Outside an active fade, FTB draws its usual quad.
        int size = MapTileFades.CHUNKS_PER_AXIS;
        for (int row = 0; row < size; row++) {
            float v0 = (float) row / size;
            float v1 = (float) (row + 1) / size;
            int first = 0;
            while (first < size) {
                int alpha = ALPHAS[row * size + first];
                int end = first + 1;
                while (end < size && ALPHAS[row * size + end] == alpha) end++;
                float u0 = (float) first / size;
                float u1 = (float) end / size;
                addQuad(buffer, pose, x + u0 * width, y + v0 * height, x + u1 * width, y + v1 * height,
                        u0, v0, u1, v1, alpha);
                first = end;
            }
        }
        BufferUploader.drawWithShader(buffer.end());
        return true;
    }

    private static void addQuad(BufferBuilder buffer, Matrix4f pose, float x0, float y0, float x1, float y1,
                                float u0, float v0, float u1, float v1, int alpha) {
        buffer.vertex(pose, x0, y1, 0).color(255, 255, 255, alpha).uv(u0, v1).endVertex();
        buffer.vertex(pose, x1, y1, 0).color(255, 255, 255, alpha).uv(u1, v1).endVertex();
        buffer.vertex(pose, x1, y0, 0).color(255, 255, 255, alpha).uv(u1, v0).endVertex();
        buffer.vertex(pose, x0, y0, 0).color(255, 255, 255, alpha).uv(u0, v0).endVertex();
    }
}

package art.arcane.auram.client.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

final class MenuMotion {
    private static final long FADE_NANOS = 1_000_000_000L;
    private final MenuFrame frame = new MenuFrame();
    private long openedAt;
    private long closedAt;
    private ClientLevel closingLevel;
    private float openingCover;

    static float cover(long started, long now) {
        if (started == 0) return 0;
        float progress = Mth.clamp((now - started) / (float) FADE_NANOS, 0, 1);
        return (float) ((Math.pow(2, -8 * progress) - 1.0 / 256.0) / (1 - 1.0 / 256.0));
    }

    void startOpen(long now) {
        openedAt = now;
    }

    void captureOpening(Minecraft client, GuiGraphics graphics, long now) {
        closingLevel = null;
        openingCover = cover(openedAt, now);
        if (openingCover > 0 && MenuGlass.ready()) {
            graphics.flush();
            capture(client);
        }
    }

    void drawOpening(GuiGraphics graphics) {
        if (openingCover > 0 && MenuGlass.ready()) draw(graphics, openingCover);
        openingCover = 0;
    }

    void captureClosing(Minecraft client) {
        if (!MenuGlass.ready()) return;
        capture(client);
        closingLevel = client.level;
        closedAt = System.nanoTime();
    }

    void drawClosing(Minecraft client, GuiGraphics graphics) {
        if (closingLevel == null) return;
        float opacity = cover(closedAt, System.nanoTime());
        if (client.level != closingLevel || client.screen != null || opacity <= 0) {
            closingLevel = null;
        } else {
            draw(graphics, opacity);
        }
    }

    private void capture(Minecraft client) {
        var main = client.getMainRenderTarget();
        frame.capture(client, main.viewHeight, main.viewWidth, main.viewHeight);
    }

    private void draw(GuiGraphics graphics, float opacity) {
        graphics.flush();
        MenuGlass.drawFrame(graphics, frame.texture(), graphics.guiWidth(), graphics.guiHeight(), 0, 0, opacity);
    }
}

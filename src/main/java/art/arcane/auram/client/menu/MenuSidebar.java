package art.arcane.auram.client.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.List;

final class MenuSidebar {
    private static final int ROW_HEIGHT = 34;
    private static final int INSET = 5;
    private static final int ICON_SIZE = 16;
    private static final int EXPANDED_WIDTH = 148;

    private final MenuGlass glass = new MenuGlass();
    private float width = AuramMenu.RAIL_WIDTH;
    private float scroll;
    private boolean expanded;
    private long lastFrame;
    private AuramMenu.Tab lastSelected;

    void draw(Minecraft client, GuiGraphics graphics, int screenWidth, int height, int mouseX, int mouseY,
              AuramMenu.Tab selected, List<AuramMenu.Tab> tabs, boolean enabled) {
        int expandedWidth = Math.max(AuramMenu.RAIL_WIDTH, Math.min(EXPANDED_WIDTH, screenWidth / 2));
        int hoverWidth = expanded ? expandedWidth : AuramMenu.RAIL_WIDTH;
        expanded = enabled && mouseX >= 0 && mouseX < hoverWidth && mouseY >= 0 && mouseY < height;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 1_000_000_000F, 0.05F);
        lastFrame = now;
        int target = expanded ? expandedWidth : AuramMenu.RAIL_WIDTH;
        width = Mth.approach(width, target, dt * 850);
        int panelWidth = Math.min(screenWidth, Math.round(width));
        if (lastSelected != selected) {
            int row = tabs.indexOf(selected) * ROW_HEIGHT;
            if (row < scroll) scroll = row;
            if (row + ROW_HEIGHT > scroll + height - 2 * INSET) scroll = row + ROW_HEIGHT - height + 2 * INSET;
            lastSelected = selected;
        }
        scroll = Mth.clamp(scroll, 0, maxScroll(height, tabs.size()));

        glass.draw(client, graphics, screenWidth, height, panelWidth, expandedWidth);
        graphics.vLine(panelWidth - 1, -1, height, 0xff354956);
        graphics.enableScissor(0, Math.min(INSET, height), panelWidth, Math.max(INSET, height - INSET));
        for (int i = 0; i < tabs.size(); i++) {
            int top = INSET + i * ROW_HEIGHT - (int) scroll;
            if (top + ROW_HEIGHT <= INSET || top >= height - INSET) continue;
            var tab = tabs.get(i);
            boolean active = tab == selected;
            boolean hovered = enabled && mouseX >= 0 && mouseX < panelWidth && mouseY >= Math.max(INSET, top)
                    && mouseY < Math.min(height - INSET, top + ROW_HEIGHT);
            if (active || hovered) {
                graphics.fillGradient(3, top + 2, panelWidth - 3, top + ROW_HEIGHT - 2,
                        active ? 0x50367d89 : 0x30366273, active ? 0x29296169 : 0x20265157);
                if (active) graphics.fill(2, top + 7, 4, top + ROW_HEIGHT - 7, 0xff9feee5);
            }
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.blit(tab.icon, (AuramMenu.RAIL_WIDTH - ICON_SIZE) / 2,
                    top + (ROW_HEIGHT - ICON_SIZE) / 2, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            RenderSystem.disableBlend();
            if (panelWidth > AuramMenu.RAIL_WIDTH) {
                graphics.drawString(client.font, tab.title, AuramMenu.RAIL_WIDTH + 3,
                        top + (ROW_HEIGHT - client.font.lineHeight) / 2, active ? 0xe6fffa : 0xd0dce4);
            }
        }
        graphics.disableScissor();

        int overflow = maxScroll(height, tabs.size());
        if (overflow > 0 && height > 2 * INSET) {
            int track = height - 2 * INSET;
            int thumb = Math.min(track, Math.max(8, track * track / (tabs.size() * ROW_HEIGHT)));
            int top = INSET + Math.round((track - thumb) * scroll / overflow);
            graphics.fill(panelWidth - 3, top, panelWidth - 1, top + thumb, 0xa87a9ca8);
        }
    }

    boolean contains(double x, double y, int height) {
        return x >= 0 && x < Math.round(width) && y >= 0 && y < height;
    }

    AuramMenu.Tab tabAt(double y, int height, List<AuramMenu.Tab> tabs) {
        int index = ((int) y - INSET + (int) scroll) / ROW_HEIGHT;
        return y >= INSET && y < height - INSET && index < tabs.size() ? tabs.get(index) : null;
    }

    void scroll(double delta, int height, int tabCount) {
        scroll = Mth.clamp(scroll - (float) delta * ROW_HEIGHT, 0, maxScroll(height, tabCount));
    }

    private static int maxScroll(int height, int tabCount) {
        return Math.max(0, tabCount * ROW_HEIGHT + 2 * INSET - height);
    }

    void reset() {
        width = AuramMenu.RAIL_WIDTH;
        scroll = 0;
        expanded = false;
        lastFrame = 0;
        lastSelected = null;
    }
}

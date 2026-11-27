package art.arcane.auram.client.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.util.Mth;

import java.util.List;

final class MenuSidebar {
    private static final int ROW_HEIGHT = 34;
    private static final int INSET = 5;
    private static final int ICON_SIZE = 16;
    private static final int ICON_LEFT = (AuramMenu.RAIL_WIDTH - ICON_SIZE) / 2;
    private static final int ICON_TOP = (ROW_HEIGHT - ICON_SIZE) / 2;
    private static final int EXPANDED_WIDTH = 148;

    private final MenuGlass glass = new MenuGlass();
    private float width = AuramMenu.RAIL_WIDTH;
    private float scroll;
    private boolean expanded;
    private long lastFrame;

    void draw(Minecraft client, GuiGraphics graphics, int screenWidth, int height, int mouseX, int mouseY,
              AuramMenu.Tab selected, Rows rows, boolean enabled) {
        int expandedWidth = Math.max(AuramMenu.RAIL_WIDTH, Math.min(EXPANDED_WIDTH, screenWidth / 2));
        scroll = Mth.clamp(scroll, 0, maxScroll(height, rows.size()));
        int origin = INSET - (int) scroll;
        int hoveredRow = rowAt(mouseY, height, rows.size());
        int iconTop = origin + hoveredRow * ROW_HEIGHT + ICON_TOP;
        boolean overIcon = mouseX >= ICON_LEFT && mouseX < ICON_LEFT + ICON_SIZE
                && mouseY >= iconTop && mouseY < iconTop + ICON_SIZE;
        boolean overExpandedRow = expanded && mouseX >= 0 && mouseX < expandedWidth;
        expanded = enabled && hoveredRow >= 0 && (overIcon || overExpandedRow);
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 1_000_000_000F, 0.05F);
        lastFrame = now;
        int target = expanded ? expandedWidth : AuramMenu.RAIL_WIDTH;
        width = Mth.approach(width, target, dt * 850);
        int panelWidth = Math.min(screenWidth, Math.round(width));

        glass.draw(client, graphics, screenWidth, height, panelWidth, expandedWidth);
        graphics.vLine(panelWidth - 1, -1, height, 0xff354956);
        graphics.enableScissor(0, Math.min(INSET, height), panelWidth, Math.max(INSET, height - INSET));
        int first = (int) scroll / ROW_HEIGHT;
        int last = Math.min(rows.size(), Math.max(first, (height - INSET - origin + ROW_HEIGHT - 1) / ROW_HEIGHT));
        for (int i = first; i < last; i++) {
            int top = origin + i * ROW_HEIGHT;
            var category = i >= rows.tabs().size() ? rows.categories().get(i - rows.tabs().size()) : null;
            var tab = category == null ? rows.tabs().get(i) : null;
            boolean active = category == null ? tab == selected : category == rows.selectedCategory();
            boolean hovered = enabled && i == hoveredRow && mouseX >= 0 && mouseX < panelWidth;
            if (i == rows.tabs().size()) graphics.hLine(6, panelWidth - 7, top, 0x80557385);
            if (active || hovered) {
                graphics.fillGradient(3, top + 2, panelWidth - 3, top + ROW_HEIGHT - 2,
                        active ? 0x50367d89 : 0x30366273, active ? 0x29296169 : 0x20265157);
                if (active) graphics.fill(2, top + 7, 4, top + ROW_HEIGHT - 7, 0xff9feee5);
            }
            if (panelWidth > AuramMenu.RAIL_WIDTH) {
                var title = category == null ? tab.title : category.getTitle();
                graphics.drawString(client.font, title, AuramMenu.RAIL_WIDTH + 3,
                        top + (ROW_HEIGHT - client.font.lineHeight) / 2, active ? 0xe6fffa : 0xd0dce4);
            }
        }
        // Flush buffered text and row backgrounds before drawing the immediate icon quads.
        graphics.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int i = first; i < Math.min(last, rows.tabs().size()); i++) {
            graphics.blit(rows.tabs().get(i).icon, ICON_LEFT, origin + i * ROW_HEIGHT + ICON_TOP, 0, 0,
                    ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        }
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        for (int i = Math.max(first, rows.tabs().size()); i < last; i++) {
            var icon = rows.categories().get(i - rows.tabs().size()).getDisplay().getIcon();
            graphics.renderFakeItem(icon, ICON_LEFT, origin + i * ROW_HEIGHT + ICON_TOP);
        }
        graphics.disableScissor();

        int overflow = maxScroll(height, rows.size());
        if (overflow > 0 && height > 2 * INSET) {
            int track = height - 2 * INSET;
            int thumb = Math.min(track, Math.max(8, track * track / (rows.size() * ROW_HEIGHT)));
            int top = INSET + Math.round((track - thumb) * scroll / overflow);
            graphics.fill(panelWidth - 3, top, panelWidth - 1, top + thumb, 0xa87a9ca8);
        }
    }

    boolean contains(double x, double y, int height) {
        return x >= 0 && x < Math.round(width) && y >= 0 && y < height;
    }

    int rowAt(double y, int height, int tabCount) {
        if (y < INSET || y >= height - INSET) return -1;
        int index = ((int) y - INSET + (int) scroll) / ROW_HEIGHT;
        return index < tabCount ? index : -1;
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
    }

    record Rows(List<AuramMenu.Tab> tabs, List<AdvancementTab> categories, AdvancementTab selectedCategory) {
        int size() {
            return tabs.size() + categories.size();
        }
    }
}

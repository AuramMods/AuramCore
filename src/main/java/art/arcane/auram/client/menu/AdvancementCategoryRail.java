package art.arcane.auram.client.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.util.Mth;

import java.util.List;

final class AdvancementCategoryRail {
    private static final int TOP = AuramMenu.BAR_HEIGHT;
    private static final int ROW_HEIGHT = 32;
    private static final int INSET = 5;
    private static final int ICON_SIZE = 16;
    private static final int RAIL_WIDTH = 36;
    private static final int ICON_LEFT = (RAIL_WIDTH - ICON_SIZE) / 2;
    private static final int ICON_TOP = (ROW_HEIGHT - ICON_SIZE) / 2;
    private static final int EXPANDED_WIDTH = 148;

    private final MenuGlass glass = new MenuGlass();
    private float width = RAIL_WIDTH;
    private float scroll;
    private boolean expanded;
    private int dismissedIcon = -1;
    private long lastFrame;

    void draw(Minecraft client, GuiGraphics graphics, int screenWidth, int height, int mouseX, int mouseY,
              List<AdvancementTab> categories, AdvancementTab selected) {
        if (categories.isEmpty() || height <= TOP + 2 * INSET) return;
        int expandedWidth = Math.max(RAIL_WIDTH, Math.min(EXPANDED_WIDTH, screenWidth / 2));
        scroll = Mth.clamp(scroll, 0, maxScroll(height, categories.size()));
        int origin = TOP + INSET - (int) scroll;
        int hoveredRow = rowAt(mouseY, height, categories.size());
        int icon = iconAt(mouseX, mouseY, hoveredRow);
        if (icon != dismissedIcon) dismissedIcon = -1;
        boolean overExpandedRow = expanded && mouseX >= 0 && mouseX < expandedWidth;
        expanded = hoveredRow >= 0 && (overExpandedRow || icon >= 0 && icon != dismissedIcon);
        long now = System.nanoTime();
        float delta = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 1_000_000_000F, 0.05F);
        lastFrame = now;
        width = Mth.approach(width, expanded ? expandedWidth : RAIL_WIDTH, delta * 850);
        int panelWidth = Math.min(screenWidth, Math.round(width));

        glass.drawRail(client, graphics, screenWidth, height, TOP, panelWidth, expandedWidth);
        graphics.vLine(panelWidth - 1, TOP - 1, height, 0xff354956);
        graphics.enableScissor(0, TOP + INSET, panelWidth, height - INSET);
        int first = (int) scroll / ROW_HEIGHT;
        int last = Math.min(categories.size(), (height - INSET - origin + ROW_HEIGHT - 1) / ROW_HEIGHT);
        for (int i = first; i < last; i++) {
            int top = origin + i * ROW_HEIGHT;
            var category = categories.get(i);
            boolean active = category == selected;
            boolean hovered = i == hoveredRow && mouseX >= 0 && mouseX < panelWidth;
            if (active || hovered) {
                graphics.fillGradient(3, top + 2, panelWidth - 3, top + ROW_HEIGHT - 2,
                        active ? 0x50367d89 : 0x30366273, active ? 0x29296169 : 0x20265157);
                if (active) graphics.fill(2, top + 7, 4, top + ROW_HEIGHT - 7, 0xff9feee5);
            }
            if (panelWidth > RAIL_WIDTH) {
                graphics.drawString(client.font, category.getTitle(), RAIL_WIDTH + 3,
                        top + (ROW_HEIGHT - client.font.lineHeight) / 2, active ? 0xe6fffa : 0xd0dce4);
            }
            graphics.renderFakeItem(category.getDisplay().getIcon(), ICON_LEFT, top + ICON_TOP);
        }
        graphics.disableScissor();

        int overflow = maxScroll(height, categories.size());
        if (overflow > 0) {
            int track = height - TOP - 2 * INSET;
            int thumb = Math.min(track, Math.max(8, track * track / (categories.size() * ROW_HEIGHT)));
            int top = TOP + INSET + Math.round((track - thumb) * scroll / overflow);
            graphics.fill(panelWidth - 3, top, panelWidth - 1, top + thumb, 0xa87a9ca8);
        }
    }

    boolean contains(double x, double y, int height) {
        return x >= 0 && x < Math.round(width) && y >= TOP && y < height;
    }

    int rowAt(double y, int height, int categoryCount) {
        if (y < TOP + INSET || y >= height - INSET) return -1;
        int index = ((int) y - TOP - INSET + (int) scroll) / ROW_HEIGHT;
        return index < categoryCount ? index : -1;
    }

    private int iconAt(double x, double y, int row) {
        int iconTop = TOP + INSET - (int) scroll + row * ROW_HEIGHT + ICON_TOP;
        return row >= 0 && x >= ICON_LEFT && x < ICON_LEFT + ICON_SIZE && y >= iconTop && y < iconTop + ICON_SIZE
                ? row : -1;
    }

    void dismiss(double x, double y, int height, int categoryCount) {
        expanded = false;
        // A click on an icon collapses the rail even if the mouse hasn't moved yet.
        dismissedIcon = iconAt(x, y, rowAt(y, height, categoryCount));
    }

    void dismiss() {
        expanded = false;
    }

    void scroll(double delta, int height, int categoryCount) {
        scroll = Mth.clamp(scroll - (float) delta * ROW_HEIGHT, 0, maxScroll(height, categoryCount));
    }

    private static int maxScroll(int height, int categoryCount) {
        return Math.max(0, TOP + categoryCount * ROW_HEIGHT + 2 * INSET - height);
    }

    void close() {
        glass.close();
    }
}

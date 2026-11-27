package art.arcane.auram.client.menu;

import art.arcane.auram.mixin.menu.AdvancementsScreenAccess;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

public final class AuramAdvancementsScreen extends AdvancementsScreen {
    private static final int CATEGORY_X = AuramMenu.RAIL_WIDTH + 6;
    private static final int CATEGORY_Y = 6;
    private static final int CATEGORY_SIZE = 24;
    private final ClientAdvancements advancements;
    private int page;
    private boolean panning;

    AuramAdvancementsScreen(ClientAdvancements advancements) {
        super(advancements);
        this.advancements = advancements;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets(); // The full-screen category strip owns paging.
        page = Math.max(0, roots().indexOf(access().auram$selectedTab())) / pageSize();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var selected = access().auram$selectedTab();
        if (selected == null) {
            graphics.fill(0, 0, width, height, 0xff131e29);
            graphics.drawCenteredString(font, Component.translatable("advancements.empty"), width / 2, height / 2, 0xffffff);
            return;
        }

        ((AdvancementCanvas) selected).auram$drawCanvas(graphics);
        var roots = roots();
        boolean overControl = AuramMenu.sidebarContains(mouseX, mouseY, height) || overCategories(mouseX, mouseY, roots);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        selected.drawTooltips(graphics, overControl ? -1 : mouseX, mouseY, 0, 0);
        graphics.pose().popPose();
        if (roots.size() > 1) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 500);
            drawCategories(graphics, mouseX, mouseY, roots, selected);
            graphics.pose().popPose();
        }
    }

    private void drawCategories(GuiGraphics graphics, int mouseX, int mouseY,
                                List<AdvancementTab> roots, AdvancementTab selected) {
        page = Mth.clamp(page, 0, lastPage(roots));
        int first = page * pageSize();
        int count = Math.min(pageSize(), roots.size() - first);
        boolean paging = lastPage(roots) > 0;
        boolean pointer = !AuramMenu.sidebarContains(mouseX, mouseY, height);
        Component hovered = null;
        for (int i = 0; i < count; i++) {
            var root = roots.get(first + i);
            int x = CATEGORY_X + i * CATEGORY_SIZE;
            boolean over = pointer && overCategoryRow(mouseY) && mouseX >= x && mouseX < x + CATEGORY_SIZE;
            drawCategoryCell(graphics, x, root == selected, over);
            graphics.renderFakeItem(root.getDisplay().getIcon(), x + 4, CATEGORY_Y + 4);
            if (over) hovered = root.getTitle();
        }
        if (paging) {
            int x = CATEGORY_X + pageSize() * CATEGORY_SIZE;
            drawArrow(graphics, x, "<", page > 0, pointer && overCategoryRow(mouseY) && mouseX >= x && mouseX < x + CATEGORY_SIZE);
            x += CATEGORY_SIZE;
            drawArrow(graphics, x, ">", page < lastPage(roots),
                    pointer && overCategoryRow(mouseY) && mouseX >= x && mouseX < x + CATEGORY_SIZE);
        }
        if (hovered != null) graphics.renderTooltip(font, hovered, mouseX, CATEGORY_Y + CATEGORY_SIZE + 12);
    }

    private void drawArrow(GuiGraphics graphics, int x, String text, boolean enabled, boolean hovered) {
        drawCategoryCell(graphics, x, false, enabled && hovered);
        graphics.drawCenteredString(font, text, x + CATEGORY_SIZE / 2,
                CATEGORY_Y + (CATEGORY_SIZE - font.lineHeight) / 2, enabled ? 0xe6fffa : 0x607382);
    }

    private static void drawCategoryCell(GuiGraphics graphics, int x, boolean selected, boolean hovered) {
        graphics.fill(x, CATEGORY_Y, x + CATEGORY_SIZE - 1, CATEGORY_Y + CATEGORY_SIZE,
                selected ? 0xe4366771 : hovered ? 0xe22b4757 : 0xd6182735);
        if (selected) graphics.hLine(x + 3, x + CATEGORY_SIZE - 5, CATEGORY_Y + CATEGORY_SIZE - 2, 0xff9feee5);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;
        var roots = roots();
        if (!overCategories(x, y, roots)) {
            panning = access().auram$selectedTab() != null;
            return panning;
        }
        int cell = ((int) x - CATEGORY_X) / CATEGORY_SIZE;
        int index = page * pageSize() + cell;
        if (cell == pageSize()) page = Math.max(0, page - 1);
        else if (cell == pageSize() + 1) page = Math.min(lastPage(roots), page + 1);
        else if (index < roots.size()) advancements.setSelectedTab(roots.get(index).getAdvancement(), true);
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        var selected = access().auram$selectedTab();
        if (button != 0 || !panning || selected == null) return false;
        ((AdvancementCanvas) selected).auram$pan(dx, dy);
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button != 0 || !panning) return false;
        panning = false;
        return true;
    }

    private boolean overCategories(double x, double y, List<AdvancementTab> roots) {
        if (roots.size() <= 1 || !overCategoryRow(y)) return false;
        int cells = lastPage(roots) > 0 ? pageSize() + 2 : roots.size();
        return x >= CATEGORY_X && x < CATEGORY_X + cells * CATEGORY_SIZE;
    }

    private static boolean overCategoryRow(double y) {
        return y >= CATEGORY_Y && y < CATEGORY_Y + CATEGORY_SIZE;
    }

    private int pageSize() {
        return Math.max(1, (width - CATEGORY_X - 6) / CATEGORY_SIZE - 2);
    }

    private int lastPage(List<AdvancementTab> roots) {
        return Math.max(0, (roots.size() - 1) / pageSize());
    }

    private List<AdvancementTab> roots() {
        return List.copyOf(access().auram$tabs().values());
    }

    private AdvancementsScreenAccess access() {
        return (AdvancementsScreenAccess) (Object) this;
    }
}

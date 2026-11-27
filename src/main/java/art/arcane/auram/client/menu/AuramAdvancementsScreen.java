package art.arcane.auram.client.menu;

import art.arcane.auram.mixin.menu.AdvancementsScreenAccess;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class AuramAdvancementsScreen extends AdvancementsScreen {
    private final ClientAdvancements advancements;
    private final AdvancementCategoryRail categoryRail = new AdvancementCategoryRail();
    private boolean panning;

    AuramAdvancementsScreen(ClientAdvancements advancements) {
        super(advancements);
        this.advancements = advancements;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets(); // Auram’s sidebar owns category navigation.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var selected = selectedCategory();
        if (selected == null) {
            graphics.fill(0, 0, width, height, 0xff131e29);
            graphics.drawCenteredString(font, Component.translatable("advancements.empty"), width / 2, height / 2, 0xffffff);
            return;
        }

        ((AdvancementCanvas) selected).auram$drawCanvas(graphics);
        boolean overControl = mouseY < AuramMenu.BAR_HEIGHT
                || categoryRail.contains(mouseX, mouseY, height);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        selected.drawTooltips(graphics, overControl ? -1 : mouseX, mouseY, 0, 0);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        var categories = categories();
        categoryRail.dismiss(x, y, height, categories.size());
        if (!categories.isEmpty() && categoryRail.contains(x, y, height)) {
            int row = categoryRail.rowAt(y, height, categories.size());
            if (button == 0 && row >= 0) selectCategory(categories.get(row));
            return true;
        }
        panning = button == 0 && selectedCategory() != null;
        return panning;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        int count = access().auram$tabs().size();
        if (count == 0 || !categoryRail.contains(x, y, height)) return false;
        categoryRail.scroll(delta, height, count);
        return true;
    }

    @Override
    public void removed() {
        categoryRail.close();
        super.removed();
    }

    void renderCategoryRail(GuiGraphics graphics, int mouseX, int mouseY) {
        categoryRail.draw(minecraft, graphics, width, height, mouseX, mouseY, categories(), selectedCategory());
    }

    void dismissCategoryRail() {
        categoryRail.dismiss();
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        var selected = selectedCategory();
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

    private List<AdvancementTab> categories() {
        return List.copyOf(access().auram$tabs().values());
    }

    private AdvancementTab selectedCategory() {
        return access().auram$selectedTab();
    }

    private void selectCategory(AdvancementTab category) {
        if (category != selectedCategory()) advancements.setSelectedTab(category.getAdvancement(), true);
    }

    private AdvancementsScreenAccess access() {
        return (AdvancementsScreenAccess) (Object) this;
    }
}

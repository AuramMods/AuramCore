package art.arcane.auram.mixin.tweak;

import art.arcane.auram.client.scroll.CreativePreviewSlot;
import art.arcane.auram.client.scroll.CreativeScrollViewport;
import art.arcane.auram.client.scroll.SmoothScrolling;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScrollMixin extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu>
        implements CreativeScrollViewport {
    @Shadow @Final private static SimpleContainer CONTAINER;
    @Shadow private float scrollOffs;
    @Shadow private boolean canScroll() { throw new AssertionError(); }

    @Unique private double auram$row = Double.NaN;
    @Unique private double auram$thumb;
    @Unique private int auram$populatedRow = -1;
    @Unique private int auram$pixelShift;
    @Unique private final Slot[] auram$preview = new Slot[COLUMNS];

    protected CreativeScrollMixin(CreativeModeInventoryScreen.ItemPickerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @WrapMethod(method = "renderBg")
    private void auram$animateGrid(GuiGraphics graphics, float tick, int x, int y, Operation<Void> draw) {
        double smoothness = SmoothScrolling.settings().creative();
        float target = scrollOffs;
        if (smoothness == 0 || !canScroll()) {
            if (auram$populatedRow >= 0) menu.scrollTo(target);
            auram$reset();
            draw.call(graphics, tick, x, y);
            return;
        }
        int maxRow = Mth.positiveCeilDiv(menu.items.size(), COLUMNS) - ROWS;
        int targetRow = Math.round(target * maxRow);
        if (!Double.isFinite(auram$row)) {
            auram$row = targetRow;
            auram$thumb = target;
        }
        auram$row = Mth.clamp(SmoothScrolling.advance(auram$row, targetRow, smoothness), 0, maxRow);
        auram$thumb = SmoothScrolling.advance(auram$thumb, target, smoothness);
        int pixels = (int) Math.round(auram$row * SLOT_SIZE);
        int row = pixels / SLOT_SIZE;
        auram$pixelShift = pixels % SLOT_SIZE;
        if (row != auram$populatedRow) {
            menu.scrollTo((float) row / maxRow);
            auram$populatedRow = row;
        }
        scrollOffs = (float) auram$thumb;
        try {
            draw.call(graphics, tick, x, y);
        } finally {
            scrollOffs = target;
        }
    }

    @WrapOperation(method = "renderBg", at = @At(value = "INVOKE", ordinal = 0, target =
            "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void auram$scrollGridTexture(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                                         int u, int v, int width, int height, Operation<Void> blit) {
        int shift = auram$pixelShift;
        if (shift == 0) {
            blit.call(graphics, texture, x, y, u, v, width, height);
            return;
        }

        // Draw the frame around the moving grid, then fill the viewport with the shifted rows.
        blit.call(graphics, texture, x, y, u, v, width, GRID_TOP);
        blit.call(graphics, texture, x, y + GRID_BOTTOM, u, v + GRID_BOTTOM, width, height - GRID_BOTTOM);
        blit.call(graphics, texture, x, y + GRID_TOP, u, v + GRID_TOP, GRID_LEFT, GRID_HEIGHT);
        blit.call(graphics, texture, x + GRID_RIGHT, y + GRID_TOP, u + GRID_RIGHT, v + GRID_TOP,
                width - GRID_RIGHT, GRID_HEIGHT);

        int gridX = x + GRID_LEFT;
        int gridY = y + GRID_TOP;
        int gridU = u + GRID_LEFT;
        int gridV = v + GRID_TOP;
        int shiftedHeight = GRID_HEIGHT - shift;
        blit.call(graphics, texture, gridX, gridY, gridU, gridV + shift, GRID_WIDTH, shiftedHeight);
        // Repeat the first slot row into the exposed strip at the bottom.
        blit.call(graphics, texture, gridX, gridY + shiftedHeight, gridU, gridV, GRID_WIDTH, shift);
    }

    @WrapOperation(method = {"mouseScrolled", "mouseDragged"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$ItemPickerMenu;scrollTo(F)V"))
    private void auram$queueGridScroll(CreativeModeInventoryScreen.ItemPickerMenu menu, float target, Operation<Void> scroll) {
        if (SmoothScrolling.settings().creative() == 0 || !canScroll()) {
            scroll.call(menu, target);
        } else if (!Double.isFinite(auram$row)) {
            auram$row = 0;
            auram$thumb = 0;
        }
    }

    @WrapOperation(method = {"init", "refreshSearchResults", "refreshCurrentTabContents", "selectTab"},
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$ItemPickerMenu;scrollTo(F)V"))
    private void auram$resetGridScroll(CreativeModeInventoryScreen.ItemPickerMenu menu, float target, Operation<Void> scroll) {
        auram$reset();
        scroll.call(menu, target);
    }

    @Unique
    private void auram$reset() {
        auram$row = Double.NaN;
        auram$pixelShift = 0;
        auram$populatedRow = -1;
    }

    @Override
    public int auram$pixelShift() {
        return auram$pixelShift;
    }

    @Override
    public boolean auram$isGridSlot(Slot slot) {
        return slot.container == CONTAINER;
    }

    @Override
    public Slot auram$previewSlot(int column) {
        if (auram$preview[column] == null) {
            int offset = column;
            auram$preview[column] = new CreativePreviewSlot(CONTAINER, column, () -> {
                int index = (auram$populatedRow + ROWS) * COLUMNS + offset;
                return index >= 0 && index < menu.items.size() ? menu.items.get(index) : ItemStack.EMPTY;
            });
        }
        return auram$preview[column];
    }
}

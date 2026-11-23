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
    @Unique private final Slot[] auram$preview = new Slot[9];

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
        int maxRow = Mth.positiveCeilDiv(menu.items.size(), 9) - 5;
        int targetRow = Math.round(target * maxRow);
        if (!Double.isFinite(auram$row)) {
            auram$row = targetRow;
            auram$thumb = target;
        }
        auram$row = Mth.clamp(SmoothScrolling.advance(auram$row, targetRow, smoothness), 0, maxRow);
        auram$thumb = SmoothScrolling.advance(auram$thumb, target, smoothness);
        int pixels = (int) Math.round(auram$row * 18);
        int row = pixels / 18;
        auram$pixelShift = pixels % 18;
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
                int index = (auram$populatedRow + 5) * 9 + offset;
                return index >= 0 && index < menu.items.size() ? menu.items.get(index) : ItemStack.EMPTY;
            });
        }
        return auram$preview[column];
    }
}

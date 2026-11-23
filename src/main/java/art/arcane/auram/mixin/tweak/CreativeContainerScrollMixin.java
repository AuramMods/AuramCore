package art.arcane.auram.mixin.tweak;

import art.arcane.auram.client.scroll.CreativeScrollViewport;
import art.arcane.auram.client.scroll.SmoothScrolling;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static art.arcane.auram.client.scroll.CreativeScrollViewport.COLUMNS;
import static art.arcane.auram.client.scroll.CreativeScrollViewport.GRID_BOTTOM;
import static art.arcane.auram.client.scroll.CreativeScrollViewport.GRID_LEFT;
import static art.arcane.auram.client.scroll.CreativeScrollViewport.GRID_RIGHT;
import static art.arcane.auram.client.scroll.CreativeScrollViewport.GRID_TOP;
import static art.arcane.auram.client.scroll.CreativeScrollViewport.SLOT_SIZE;

@Mixin(AbstractContainerScreen.class)
public abstract class CreativeContainerScrollMixin {
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected Slot hoveredSlot;
    @Shadow private void renderSlot(GuiGraphics graphics, Slot slot) { throw new AssertionError(); }
    @Shadow(remap = false) public static void renderSlotHighlight(GuiGraphics graphics, int x, int y, int z, int color) { throw new AssertionError(); }
    @Shadow protected abstract boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY);
    @Unique private boolean auram$gridClip;

    @WrapMethod(method = "render")
    private void auram$restoreClip(GuiGraphics graphics, int x, int y, float tick, Operation<Void> draw) {
        try {
            draw.call(graphics, x, y, tick);
        } finally {
            auram$closeGridClip(graphics);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V"))
    private void auram$drawGridSlot(AbstractContainerScreen<?> screen, GuiGraphics graphics, Slot slot,
                                    Operation<Void> draw, @Local(argsOnly = true, ordinal = 0) int mouseX,
                                    @Local(argsOnly = true, ordinal = 1) int mouseY) {
        if (screen instanceof CreativeScrollViewport grid && grid.auram$pixelShift() > 0) {
            if (grid.auram$isGridSlot(slot)) {
                if (!auram$gridClip) {
                    SmoothScrolling.mask(graphics, leftPos + GRID_LEFT, topPos + GRID_TOP,
                            leftPos + GRID_RIGHT, topPos + GRID_BOTTOM);
                    auram$gridClip = true;
                }
                graphics.pose().pushPose();
                graphics.pose().translate(0, -grid.auram$pixelShift(), 0);
                try {
                    draw.call(screen, graphics, slot);
                } finally {
                    graphics.pose().popPose();
                }
                return;
            }
            auram$finishGrid(graphics, mouseX, mouseY);
        }
        draw.call(screen, graphics, slot);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void auram$leaveGrid(GuiGraphics graphics, int x, int y, float tick, CallbackInfo callback) {
        auram$finishGrid(graphics, x, y);
    }

    @WrapMethod(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z")
    private boolean auram$hoverVisibleSlot(Slot slot, double x, double y, Operation<Boolean> original) {
        if ((Object) this instanceof CreativeScrollViewport grid && grid.auram$pixelShift() > 0 && grid.auram$isGridSlot(slot)) {
            return auram$insideGrid(x, y) && original.call(slot, x, y + grid.auram$pixelShift());
        }
        return original.call(slot, x, y);
    }

    @ModifyReturnValue(method = "findSlot", at = @At("RETURN"))
    private Slot auram$pickPartialRow(Slot slot, @Local(argsOnly = true, ordinal = 0) double x,
                                     @Local(argsOnly = true, ordinal = 1) double y) {
        if (slot == null && (Object) this instanceof CreativeScrollViewport grid) return auram$getPreview(grid, x, y);
        return slot;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", remap = false, target =
            "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;IIII)V"))
    private void auram$alignHighlight(GuiGraphics graphics, int x, int y, int z, int color, Operation<Void> draw,
                                      @Local Slot slot) {
        if ((Object) this instanceof CreativeScrollViewport grid && grid.auram$isGridSlot(slot)) y -= grid.auram$pixelShift();
        draw.call(graphics, x, y, z, color);
    }

    @Unique
    private void auram$finishGrid(GuiGraphics graphics, int x, int y) {
        if (!auram$gridClip || !((Object) this instanceof CreativeScrollViewport grid)) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0, -grid.auram$pixelShift(), 0);
        try {
            for (int column = 0; column < COLUMNS; column++) {
                Slot slot = grid.auram$previewSlot(column);
                if (slot.hasItem()) renderSlot(graphics, slot);
            }
            Slot hovered = auram$getPreview(grid, x, y);
            if (hovered != null) {
                hoveredSlot = hovered;
                renderSlotHighlight(graphics, hovered.x, hovered.y, 0, 0x80FFFFFF);
            }
        } finally {
            graphics.pose().popPose();
            auram$closeGridClip(graphics);
        }
    }

    @Unique
    private Slot auram$getPreview(CreativeScrollViewport grid, double x, double y) {
        if (grid.auram$pixelShift() == 0 || !auram$insideGrid(x, y)) return null;
        double adjustedY = y + grid.auram$pixelShift();
        int column = (int) ((x - leftPos - GRID_LEFT) / SLOT_SIZE);
        Slot preview = grid.auram$previewSlot(column);
        return isHovering(preview.x, preview.y, 16, 16, x, adjustedY) ? preview : null;
    }

    @Unique
    private boolean auram$insideGrid(double x, double y) {
        return x >= leftPos + GRID_LEFT && x < leftPos + GRID_RIGHT
                && y >= topPos + GRID_TOP && y < topPos + GRID_BOTTOM;
    }

    @Unique
    private void auram$closeGridClip(GuiGraphics graphics) {
        if (!auram$gridClip) return;
        graphics.disableScissor();
        auram$gridClip = false;
    }
}

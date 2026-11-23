package art.arcane.auram.mixin.tweak;

import art.arcane.auram.client.scroll.HotbarScrollInput;
import art.arcane.auram.client.scroll.SmoothScrolling;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public abstract class HotbarScrollMixin {
    @Unique private static final int AURAM_ROLL_WIDTH = 184;
    @Unique private double auram$selectedX;
    @Unique private Inventory auram$inventory;

    @Shadow protected abstract Player getCameraPlayer();

    @WrapOperation(method = "renderHotbar", at = @At(value = "INVOKE", ordinal = 1, target =
            "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void auram$animateSelection(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                                        int u, int v, int w, int h, Operation<Void> blit) {
        Player player = getCameraPlayer();
        if (player == null) {
            blit.call(graphics, texture, x, y, u, v, w, h);
            return;
        }
        Inventory inventory = player.getInventory();
        int wraps = ((HotbarScrollInput) inventory).auram$consumeWraps();
        int target = inventory.selected * 20;
        var settings = SmoothScrolling.settings();
        double smoothness = settings.hotbar();
        if (inventory != auram$inventory || smoothness == 0) {
            auram$inventory = inventory;
            auram$selectedX = target;
            blit.call(graphics, texture, x, y, u, v, w, h);
            return;
        }
        if (settings.rollover()) auram$selectedX -= (double) wraps * AURAM_ROLL_WIDTH;
        auram$selectedX = SmoothScrolling.advance(auram$selectedX, target, smoothness);
        int start = x - target;
        int position = (int) Math.round(auram$selectedX);
        if (position >= 0 && position <= 160) {
            blit.call(graphics, texture, start + position, y, u, v, w, h);
            return;
        }

        position = Math.floorMod(position, AURAM_ROLL_WIDTH);
        SmoothScrolling.mask(graphics, start, y, start + AURAM_ROLL_WIDTH, y + h + 2);
        try {
            blit.call(graphics, texture, start + position, y, u, v, w, h);
            blit.call(graphics, texture, start + position - AURAM_ROLL_WIDTH, y, u, v, w, h);
        } finally {
            graphics.disableScissor();
        }
    }
}

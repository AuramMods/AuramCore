package art.arcane.auram.mixin.menu;

import art.arcane.auram.client.menu.AuramMenu;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(AdvancementsScreen.class)
abstract class AdvancementsLayoutMixin {
    private static final int TAB_OVERHANG = 28;

    @ModifyExpressionValue(method = {"init", "render", "mouseClicked"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/advancements/AdvancementsScreen;height:I"))
    private int auram$reserveHeaderSpace(int height) {
        // Shift the window down to clear the header, leaving room for its bottom tabs.
        int windowHeight = AdvancementsScreen.WINDOW_HEIGHT;
        int heightForPager = windowHeight + 2 * (AuramMenu.BAR_HEIGHT + 50);
        int heightWithFooter = 2 * (height - TAB_OVERHANG) - windowHeight;
        return Math.max(height, Math.min(heightForPager, heightWithFooter));
    }

    @ModifyArg(method = "init", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/components/Button$Builder;pos(II)Lnet/minecraft/client/gui/components/Button$Builder;"),
            index = 1, require = 0)
    private int auram$movePagerBelowHeader(int y) {
        return Math.max(y, AuramMenu.BAR_HEIGHT);
    }

    @ModifyArg(method = "init", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/components/Button$Builder;size(II)Lnet/minecraft/client/gui/components/Button$Builder;"),
            index = 1, require = 0)
    private int auram$fitPagerAboveTabs(int buttonHeight) {
        int height = ((AdvancementsScreen) (Object) this).height;
        int windowTop = (auram$reserveHeaderSpace(height) - AdvancementsScreen.WINDOW_HEIGHT) / 2;
        return Math.min(buttonHeight, Math.max(1, windowTop - TAB_OVERHANG - AuramMenu.BAR_HEIGHT));
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"),
            index = 3, require = 0)
    private int auram$movePageNumberBelowHeader(int y) {
        return Math.max(y, AuramMenu.BAR_HEIGHT + 3);
    }
}

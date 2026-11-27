package art.arcane.auram.mixin.adapt;

import art.arcane.adapt.client.ui.kit.Popover;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.auram.client.menu.AdaptOverviewRendering;
import art.arcane.auram.client.menu.AdaptSkillTree;
import art.arcane.auram.client.menu.AuramMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Popover.class, remap = false)
abstract class AdaptOverviewPopoverMixin {
    private static final int AURAM_BAR_PADDING = 4;

    @WrapOperation(method = "layout", at = @At(value = "INVOKE",
            target = "Lart/arcane/adapt/client/ui/kit/Popover;place(Lart/arcane/adapt/client/ui/kit/UiRect;IIII)Lart/arcane/adapt/client/ui/kit/UiRect;"))
    private UiRect auram$keepClearOfMenuBars(UiRect anchor, int width, int height, int screenWidth, int screenHeight,
                                             Operation<UiRect> original) {
        if (!AdaptOverviewRendering.active()) {
            return original.call(anchor, width, height, screenWidth, screenHeight);
        }
        // Adapt lays out the panel and its scroll area together. Give it the usable viewport.
        int top = AuramMenu.BAR_HEIGHT + AURAM_BAR_PADDING;
        int bottom = AdaptSkillTree.footerTop(screenHeight) - AURAM_BAR_PADDING;
        UiRect fit = original.call(anchor.offset(0, -top), width, height, screenWidth, Math.max(8, bottom - top));
        return fit.offset(0, top);
    }
}

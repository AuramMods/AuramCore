package art.arcane.auram.mixin.tweak;

import art.arcane.auram.client.scroll.SmoothScrolling;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(CommandSuggestions.SuggestionsList.class)
public abstract class SuggestionScrollMixin {
    @Shadow private int offset;
    @Shadow @Final private Rect2i rect;
    @Shadow @Final private List<Suggestion> suggestionList;
    @Unique private double auram$position = Double.NaN;
    @Unique private int auram$displayedOffset;
    @Unique private int auram$pixelShift;
    @Unique private boolean auram$masked;

    @WrapMethod(method = "render")
    private void auram$animateSuggestions(GuiGraphics graphics, int x, int y, Operation<Void> render) {
        double factor = SmoothScrolling.settings().chat();
        int target = offset;
        if (!Double.isFinite(auram$position)) auram$position = target;
        auram$position = SmoothScrolling.advance(auram$position, target, factor);
        auram$displayedOffset = Mth.floor(auram$position);
        auram$pixelShift = (int) Math.floor((auram$position - auram$displayedOffset) * 12);
        offset = auram$displayedOffset;
        try {
            render.call(graphics, x, rect.contains(x, y) ? y + auram$pixelShift : y);
        } finally {
            offset = target;
            auram$unmask(graphics);
        }
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", remap = false))
    private int auram$includePartialRow(int ordinaryRows) {
        return auram$pixelShift == 0 ? ordinaryRows : Math.min(ordinaryRows + 1, suggestionList.size() - offset);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", ordinal = 4, target =
            "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void auram$scrollRows(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, Operation<Void> draw) {
        if (auram$pixelShift != 0 && !auram$masked) {
            SmoothScrolling.mask(graphics, rect.getX(), rect.getY(), rect.getX() + rect.getWidth(), rect.getY() + rect.getHeight());
            graphics.pose().pushPose();
            graphics.pose().translate(0, -auram$pixelShift, 0);
            auram$masked = true;
        }
        draw.call(graphics, x1, y1, x2, y2, color);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;II)V"))
    private void auram$drawTooltip(GuiGraphics graphics, Font font, Component text, int x, int y, Operation<Void> draw) {
        auram$unmask(graphics);
        int mouseY = y - auram$pixelShift;
        if (rect.contains(x, mouseY)) draw.call(graphics, font, text, x, mouseY);
    }

    @WrapMethod(method = "mouseClicked")
    private boolean auram$clickVisibleSuggestion(int x, int y, int button, Operation<Boolean> click) {
        if (SmoothScrolling.settings().chat() == 0 || !rect.contains(x, y)) return click.call(x, y, button);
        int target = offset;
        offset = auram$displayedOffset;
        // The last partial row is visible, but vanilla bounds-checks the click before computing its index.
        int shifted = y + auram$pixelShift;
        if (shifted >= rect.getY() + rect.getHeight()) {
            offset++;
            shifted -= 12;
        }
        try {
            return click.call(x, shifted, button);
        } finally {
            offset = target;
        }
    }

    @Unique
    private void auram$unmask(GuiGraphics graphics) {
        if (!auram$masked) return;
        graphics.pose().popPose();
        graphics.disableScissor();
        auram$masked = false;
    }
}

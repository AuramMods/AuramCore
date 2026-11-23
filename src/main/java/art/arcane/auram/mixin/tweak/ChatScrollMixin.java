package art.arcane.auram.mixin.tweak;

import art.arcane.auram.client.scroll.SmoothScrolling;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class ChatScrollMixin {
    @Shadow private int chatScrollbarPos;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow public abstract int getLinesPerPage();
    @Shadow public abstract int getWidth();
    @Shadow public abstract double getScale();
    @Shadow private int getLineHeight() { throw new AssertionError(); }
    @Shadow private boolean isChatFocused() { throw new AssertionError(); }
    @Unique private double auram$visibleHistory;
    @Unique private double auram$visibleHeight;
    @Unique private double auram$historyFraction;
    @Unique private int auram$historyBase;
    @Unique private int auram$height;
    @Unique private boolean auram$masking;
    @Unique private boolean auram$renderingHistory;

    @WrapMethod(method = "render")
    private void auram$animateChat(GuiGraphics graphics, int tick, int mouseX, int mouseY, Operation<Void> render) {
        var settings = SmoothScrolling.settings();
        if (settings.chat() == 0 && settings.chatOpening() == 0 || trimmedMessages.isEmpty()) {
            auram$visibleHistory = chatScrollbarPos;
            auram$visibleHeight = 0;
            auram$height = 0;
            render.call(graphics, tick, mouseX, mouseY);
            return;
        }
        int target = chatScrollbarPos;
        auram$visibleHistory = SmoothScrolling.advance(auram$visibleHistory, target, settings.chat());
        auram$visibleHistory = Mth.clamp(auram$visibleHistory, 0, trimmedMessages.size() - 1);
        auram$historyBase = Mth.floor(auram$visibleHistory);
        auram$historyFraction = auram$visibleHistory - auram$historyBase;
        int eligible = 0;
        for (int i = target; i < Math.min(target + getLinesPerPage(), trimmedMessages.size()); i++) {
            if (isChatFocused() || tick - trimmedMessages.get(i).addedTime() < 200) eligible++;
        }
        auram$visibleHeight = SmoothScrolling.advance(auram$visibleHeight, eligible * getLineHeight(), settings.chatOpening());
        auram$height = (int) Math.round(auram$visibleHeight);
        auram$renderingHistory = true;
        chatScrollbarPos = auram$historyBase;
        try {
            render.call(graphics, tick, mouseX, mouseY);
        } finally {
            auram$closeMask(graphics);
            auram$renderingHistory = false;
            chatScrollbarPos = target;
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/components/ChatComponent;getMessageEndIndexAt(DD)I"))
    private void auram$clipMessages(GuiGraphics graphics, int tick, int mouseX, int mouseY, CallbackInfo callback) {
        if (!auram$renderingHistory) return;
        double scale = getScale();
        int bottom = (int) Math.floor((graphics.guiHeight() - 40) / scale);
        SmoothScrolling.mask(graphics, 0, (int) ((bottom - auram$height) * scale),
                (int) Math.ceil(getWidth() + 12 * scale), (int) Math.ceil(bottom * scale));
        graphics.pose().pushPose();
        graphics.pose().translate(0, (float) (auram$historyFraction * getLineHeight()), 0);
        auram$masking = true;
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/ChatListener;queueSize()J"))
    private void auram$leaveMessageArea(GuiGraphics graphics, int tick, int mouseX, int mouseY, CallbackInfo callback) {
        auram$closeMask(graphics);
    }

    @Unique
    private void auram$closeMask(GuiGraphics graphics) {
        if (!auram$masking) return;
        graphics.pose().popPose();
        graphics.disableScissor();
        auram$masking = false;
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/components/ChatComponent;getLinesPerPage()I"))
    private int auram$renderPartialRow(int lines) {
        return auram$renderingHistory && auram$historyFraction > 0 ? lines + 1 : lines;
    }

    @WrapMethod(method = "getMessageLineIndexAt")
    private int auram$pickVisibleMessage(double x, double y, Operation<Integer> original) {
        var settings = SmoothScrolling.settings();
        if (settings.chat() == 0 && settings.chatOpening() == 0) return original.call(x, y);
        if (y < 0 || y * getLineHeight() >= auram$height) return -1;
        int target = chatScrollbarPos;
        chatScrollbarPos = auram$historyBase;
        try {
            return original.call(x, y + auram$historyFraction);
        } finally {
            chatScrollbarPos = target;
        }
    }

    @ModifyExpressionValue(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"))
    private List<FormattedCharSequence> auram$shiftHistory(List<FormattedCharSequence> lines,
                                                          @Local(argsOnly = true) boolean refresh) {
        if (!refresh && SmoothScrolling.settings().chat() > 0) auram$visibleHistory += lines.size();
        return lines;
    }

    @Inject(method = "clearMessages", at = @At("RETURN"))
    private void auram$resetMotion(boolean clearHistory, CallbackInfo callback) {
        auram$visibleHistory = 0;
        auram$visibleHeight = 0;
        auram$height = 0;
    }
}

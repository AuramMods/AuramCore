package art.arcane.auram.mixin.tweak;

import art.arcane.auram.client.scroll.SmoothScrolling;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSelectionList.class)
public abstract class SelectionListScrollMixin {
    @Shadow private double scrollAmount;
    @Shadow public abstract int getMaxScroll();
    @Unique private double auram$target;
    @Unique private double auram$animated;

    @Inject(method = "setScrollAmount", at = @At("RETURN"))
    private void auram$snapProgrammaticScroll(double requested, CallbackInfo callback) {
        auram$target = scrollAmount;
        auram$animated = scrollAmount;
    }

    @WrapOperation(method = "mouseScrolled", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/components/AbstractSelectionList;setScrollAmount(D)V"))
    private void auram$queueScroll(AbstractSelectionList<?> list, double requested, Operation<Void> original,
                                   @Local(argsOnly = true, ordinal = 2) double wheel) {
        var settings = SmoothScrolling.settings();
        if (!settings.enabled()) {
            original.call(list, requested);
            return;
        }
        double distance = settings.entryListSpeed() == 0 ? requested - scrollAmount : -wheel * settings.entryListSpeed();
        if (settings.entryList() == 0) original.call(list, scrollAmount + distance);
        else auram$target = Mth.clamp(auram$target + distance, 0, getMaxScroll());
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void auram$animate(GuiGraphics graphics, int x, int y, float partialTick, CallbackInfo callback) {
        var settings = SmoothScrolling.settings();
        if (!settings.enabled()) return;
        auram$target = Mth.clamp(auram$target, 0, getMaxScroll());
        auram$animated = SmoothScrolling.advance(auram$animated, auram$target, settings.entryList());
        scrollAmount = Mth.clamp(Math.round(auram$animated), 0, getMaxScroll());
    }
}

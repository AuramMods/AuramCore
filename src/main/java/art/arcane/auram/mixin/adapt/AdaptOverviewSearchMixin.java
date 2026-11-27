package art.arcane.auram.mixin.adapt;

import art.arcane.adapt.client.ui.kit.TextField;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.auram.client.menu.AdaptOverviewRendering;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = TextField.class, remap = false)
abstract class AdaptOverviewSearchMixin {
    @ModifyArg(method = "paint", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lart/arcane/adapt/client/ui/UiDraw;text(Ljava/lang/String;IIIZ)V"), index = 3)
    private int auram$useReadablePlaceholderInk(int color, @Local(argsOnly = true) UiFrame frame) {
        if (AdaptOverviewRendering.active() && "search".equals(((TextField) (Object) this).id())) {
            return frame.theme().textMuted();
        }
        return color;
    }
}

package art.arcane.auram.mixin.tweak;

import art.arcane.auram.client.TransitionSettings;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance")
public abstract class ToastInstanceTransitionMixin {
    @ModifyReturnValue(method = "getVisibility", at = @At("RETURN"))
    private float auram$skipToastSlide(float visibility) {
        return TransitionSettings.INSTANCE.skipToastSlide() ? 1.0F : visibility;
    }
}

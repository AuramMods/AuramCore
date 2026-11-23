package art.arcane.auram.mixin.tweak;

import art.arcane.auram.client.TransitionSettings;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Toast.Visibility.class)
public abstract class ToastVisibilityTransitionMixin {
    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private void auram$skipWhoosh(SoundManager manager, CallbackInfo callback) {
        if (TransitionSettings.INSTANCE.skipToastSounds()) callback.cancel();
    }
}

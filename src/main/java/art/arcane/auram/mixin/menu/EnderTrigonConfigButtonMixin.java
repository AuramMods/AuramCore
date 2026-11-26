package art.arcane.auram.mixin.menu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "nonamecrackers2.endertrigon.client.event.EnderTrigonClientEvents", remap = false)
abstract class EnderTrigonConfigButtonMixin {
    @Inject(method = "registerConfigMenuButton(Lnonamecrackers2/crackerslib/client/event/impl/ConfigMenuButtonEvent;)V",
            at = @At("HEAD"), cancellable = true)
    private static void auram$hideOptionsShortcut(CallbackInfo ci) {
        ci.cancel();
    }
}

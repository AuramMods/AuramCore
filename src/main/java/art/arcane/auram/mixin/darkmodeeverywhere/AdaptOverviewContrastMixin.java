package art.arcane.auram.mixin.darkmodeeverywhere;

import art.arcane.auram.client.menu.AdaptOverviewRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.buuz135.darkmodeeverywhere.ClientProxy", remap = false)
abstract class AdaptOverviewContrastMixin {
    @Inject(method = "isElementNameBlacklisted", at = @At("HEAD"), cancellable = true)
    private static void auram$keepNativeAdaptContrast(String caller, CallbackInfoReturnable<Boolean> cir) {
        // Bypass both texture darkening and ink inversion for these widgets, without changing the shared cache.
        if (AdaptOverviewRendering.active()) cir.setReturnValue(true);
    }
}

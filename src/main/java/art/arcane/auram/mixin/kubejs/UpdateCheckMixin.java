package art.arcane.auram.mixin.kubejs;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.latvian.mods.kubejs.KubeJS", remap = false)
public abstract class UpdateCheckMixin {
    // KubeJS 2001.6.5-build.16 has no config switch for this startup request.
    // Cancel only the updater task; loadComplete still runs every script hook.
    @Inject(method = "lambda$loadComplete$3()V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void auram$disableStartupUpdateRequest(CallbackInfo ci) {
        ci.cancel();
    }
}

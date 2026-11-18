package art.arcane.auram.smoke.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.legacy.lucent.core.dynamic_lighting.DynamicLightingEngine", remap = false)
public abstract class LucentWorkerSmokeMixin {
    @Inject(method = "start()V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void auram$driveDeterministicTicks(CallbackInfo ci) {
        if ("memory-lucent".equals(System.getProperty("auram.smoke"))) ci.cancel();
    }
}

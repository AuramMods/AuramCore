package art.arcane.auram.mixin.zeta;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Map;

/** Quark and integrations can register handlers concurrently during mod construction. */
@Pseudo
@Mixin(targets = "org.violetmoon.zetaimplforge.event.ForgeZetaEventBus", remap = false)
public abstract class EventHandlerMapMixin {
    @Shadow @Final @Mutable private Map<Object, Object> convertedHandlers;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void auram$synchronizeHandlerMap(CallbackInfo ci) {
        // Preserve the original map's null/key/value semantics. Synchronize only
        // map operations, without holding a new lock across Forge bus callbacks.
        convertedHandlers = Collections.synchronizedMap(convertedHandlers);
    }
}

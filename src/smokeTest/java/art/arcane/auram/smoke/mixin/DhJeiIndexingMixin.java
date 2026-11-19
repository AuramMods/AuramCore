package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.DistantHorizonsSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.library.startup.JeiStarter", remap = false)
public abstract class DhJeiIndexingMixin {
    @Inject(method = {"start", "stop"}, at = @At("HEAD"), cancellable = true)
    private void auram$skipRecipeRuntimeForReplay(CallbackInfo ci) {
        if (DistantHorizonsSmoke.CLIENT && Boolean.getBoolean("auram.dh.skipJei")) ci.cancel();
    }
}

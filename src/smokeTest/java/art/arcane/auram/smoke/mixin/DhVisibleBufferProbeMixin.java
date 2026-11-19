package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.DistantHorizonsClientSmoke;
import art.arcane.auram.smoke.DistantHorizonsSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.RenderBufferHandler", remap = false)
public abstract class DhVisibleBufferProbeMixin {
    @Shadow private int visibleBufferCount;

    @Inject(method = "buildRenderList", at = @At("RETURN"))
    private void auram$observeVisibleBuffers(CallbackInfo ci) {
        if (DistantHorizonsSmoke.CLIENT) DistantHorizonsClientSmoke.visibleBuffers = visibleBufferCount;
    }
}

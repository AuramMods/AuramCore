package art.arcane.auram.mixin.vapor;

import art.arcane.auram.rf.client.RfFieldTexture;
import art.arcane.auram.tconstruct.client.TinkersFluidLightTexture;
import art.arcane.auram.vapor.client.VaporTexture;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
abstract class IrisVaporFrameMixin {
    @Inject(method = "beginLevelRendering", at = @At("HEAD"))
    private void auram$prepareVapor(CallbackInfo callback) {
        VaporTexture.prepareFrame();
        RfFieldTexture.prepareFrame();
        TinkersFluidLightTexture.prepareFrame();
    }
}

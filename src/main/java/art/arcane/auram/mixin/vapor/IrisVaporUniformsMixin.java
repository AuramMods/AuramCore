package art.arcane.auram.mixin.vapor;

import art.arcane.auram.rf.client.RfFieldTexture;
import art.arcane.auram.vapor.client.VaporTexture;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CommonUniforms.class, remap = false)
abstract class IrisVaporUniformsMixin {
    @Inject(method = "generalCommonUniforms", at = @At("TAIL"))
    private static void auram$vaporUniforms(UniformHolder uniforms, FrameUpdateNotifier notifier,
                                            PackDirectives directives, CallbackInfo callback) {
        VaporTexture.uniforms(uniforms);
        RfFieldTexture.uniforms(uniforms);
    }
}

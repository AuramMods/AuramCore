package art.arcane.auram.mixin.vapor;

import art.arcane.auram.rf.client.RfFieldTexture;
import art.arcane.auram.tconstruct.client.TinkersFluidLightTexture;
import art.arcane.auram.vapor.client.VaporTexture;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.samplers.IrisSamplers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisSamplers.class, remap = false)
abstract class IrisVaporBindingsMixin {
    @Inject(method = "addCustomTextures", at = @At("HEAD"))
    private static void auram$bindVapor(SamplerHolder samplers, Object2ObjectMap<String, TextureAccess> textures,
                                      CallbackInfo callback) {
        if (samplers.hasSampler(TinkersFluidLightTexture.SAMPLER)) {
            samplers.addDynamicSampler(TextureType.TEXTURE_3D, TinkersFluidLightTexture::texture, null,
                    TinkersFluidLightTexture.SAMPLER);
        }
        if (samplers.hasSampler(RfFieldTexture.SAMPLER)) {
            samplers.addDynamicSampler(TextureType.TEXTURE_3D, RfFieldTexture::texture, null, RfFieldTexture.SAMPLER);
        }
        if (samplers.hasSampler(VaporTexture.SAMPLER)) {
            samplers.addDynamicSampler(TextureType.TEXTURE_3D, VaporTexture::texture, null, VaporTexture.SAMPLER);
        }
    }
}

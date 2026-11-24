package art.arcane.auram.mixin.vapor;

import art.arcane.auram.vapor.client.VaporAtmosphere;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.texture.CustomTextureData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Iris.class, remap = false)
abstract class IrisVaporFogMixin {
    @Inject(method = "loadShaderpack", at = @At("RETURN"))
    private static void auram$readVaporFog(CallbackInfo callback) {
        var pack = Iris.getCurrentPack().orElse(null);
        if (pack == null) {
            VaporAtmosphere.clearBliss();
            return;
        }
        var options = pack.getShaderPackOptions();
        var keys = options.getOptionSet();
        var values = options.getOptionValues();
        if (!keys.isBooleanOption("VOLUMETRIC_VAPOR") || !keys.isBooleanOption("TOGGLE_VL_FOG")
                || !values.getBooleanValueOrDefault("TOGGLE_VL_FOG")) {
            VaporAtmosphere.clearBliss();
            return;
        }
        var noise = pack.getCustomNoiseTexture().orElse(null);
        byte[] png = noise instanceof CustomTextureData.PngData image ? image.getContent() : null;
        VaporAtmosphere.updateBliss(
                name -> keys.getStringOptions().containsKey(name) ? values.getStringValueOrDefault(name) : null,
                name -> keys.isBooleanOption(name) && values.getBooleanValueOrDefault(name), png);
    }
}

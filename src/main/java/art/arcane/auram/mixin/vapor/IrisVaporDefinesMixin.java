package art.arcane.auram.mixin.vapor;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.irisshaders.iris.gl.shader.StandardMacros;
import net.irisshaders.iris.helpers.StringPair;
import net.minecraftforge.fml.loading.FMLLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = StandardMacros.class, remap = false)
abstract class IrisVaporDefinesMixin {
    @ModifyReturnValue(method = "createStandardEnvironmentDefines", at = @At("RETURN"))
    private static ImmutableList<StringPair> auram$vaporDefine(ImmutableList<StringPair> defines) {
        var builder = ImmutableList.<StringPair>builder().addAll(defines)
                .add(new StringPair("AURAM_VAPOR_BACKEND", ""))
                .add(new StringPair("AURAM_RF_BACKEND", ""))
                .add(new StringPair("AURAM_LIGHT_BACKEND", ""));
        // Oculus can load the shader pack before Forge constructs its runtime ModList.
        if (FMLLoader.getLoadingModList().getModFileById("tconstruct") != null) {
            builder.add(new StringPair("AURAM_TINKERS_FLUID_BACKEND", ""));
        }
        return builder.build();
    }
}

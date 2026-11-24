package art.arcane.auram.mixin.vapor;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.irisshaders.iris.gl.shader.StandardMacros;
import net.irisshaders.iris.helpers.StringPair;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = StandardMacros.class, remap = false)
abstract class IrisVaporDefinesMixin {
    @ModifyReturnValue(method = "createStandardEnvironmentDefines", at = @At("RETURN"))
    private static ImmutableList<StringPair> auram$vaporDefine(ImmutableList<StringPair> defines) {
        var builder = ImmutableList.<StringPair>builder().addAll(defines)
                .add(new StringPair("AURAM_VAPOR_BACKEND", ""))
                .add(new StringPair("AURAM_RF_BACKEND", ""));
        if (ModList.get().isLoaded("tconstruct")) {
            builder.add(new StringPair("AURAM_TINKERS_FLUID_BACKEND", ""));
        }
        return builder.build();
    }
}

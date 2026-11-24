package art.arcane.auram.mixin.vapor;

import com.google.common.collect.ImmutableList;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.IrisDefines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IrisDefines.class, remap = false)
abstract class IrisVaporDefinesMixin {
    @Inject(method = "createIrisReplacements", at = @At("RETURN"), cancellable = true)
    private static void auram$vaporDefine(CallbackInfoReturnable<ImmutableList<StringPair>> callback) {
        callback.setReturnValue(ImmutableList.<StringPair>builder().addAll(callback.getReturnValue())
                .add(new StringPair("AURAM_VAPOR_BACKEND", "")).build());
    }
}

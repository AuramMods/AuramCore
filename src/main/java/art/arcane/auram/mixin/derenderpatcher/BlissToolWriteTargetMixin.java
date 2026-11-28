package art.arcane.auram.mixin.derenderpatcher;

import art.arcane.auram.draconic.client.BlissToolCapture;

import net.irisshaders.iris.Iris;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.derenderpatcher.compat.ShaderCompat", remap = false)
abstract class BlissToolWriteTargetMixin {
    @Shadow
    public static boolean shouldAllowUnknownShader(ShaderInstance shader) {
        throw new AssertionError();
    }

    @Inject(method = "allowUnknownShaderOutput", at = @At("HEAD"), cancellable = true)
    private static void auram$routeToolDraw(ShaderInstance shader, CallbackInfo ci) {
        if (BlissToolCapture.isToolShader(shader.getName()) && shouldAllowUnknownShader(shader)
                && Iris.getPipelineManager().getPipelineNullable() instanceof BlissToolCapture capture
                && capture.auram$bindToolTarget()) {
            ci.cancel();
        }
    }
}

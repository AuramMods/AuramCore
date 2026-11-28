package art.arcane.auram.mixin.derenderpatcher;

import art.arcane.auram.draconic.client.BlissToolCapture;

import com.mojang.blaze3d.vertex.VertexBuffer;
import net.irisshaders.iris.Iris;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(VertexBuffer.class)
abstract class BlissToolProjectionMixin {
    @ModifyVariable(method = "_drawWithShader", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private Matrix4f auram$applyBlissTaa(Matrix4f projection, Matrix4f modelView, Matrix4f original,
                                        ShaderInstance shader) {
        if (BlissToolCapture.isToolShader(shader.getName()) && ShaderCompatAccess.auram$shouldAllowUnknownShader(shader)
                && Iris.getPipelineManager().getPipelineNullable() instanceof BlissToolCapture capture) {
            return capture.auram$toolProjection(projection);
        }
        return projection;
    }
}

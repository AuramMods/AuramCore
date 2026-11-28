package art.arcane.auram.mixin.derenderpatcher;

import art.arcane.auram.draconic.client.BlissToolCapture;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.image.GlImage;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
abstract class BlissToolPipelineMixin implements BlissToolCapture {
    // Matches AuramBliss's TAA_jitter.glsl.
    @Unique private static final int[] AURAM_JITTER_X = {1, -1, 5, -3, -5, -7, 3, 7};
    @Unique private static final int[] AURAM_JITTER_Y = {-3, 3, 1, -5, 5, -1, 7, 7};

    @Shadow @Final private Set<GlImage> customImages;
    @Shadow @Final private RenderTargets renderTargets;

    @Unique private GlFramebuffer auram$toolTarget;
    @Unique private boolean auram$toolTaa;
    @Unique private float auram$toolScale = 1.0f;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void auram$initToolTarget(ProgramSet programs, CallbackInfo ci) {
        GlImage color = null;
        GlImage depth = null;
        for (GlImage image : customImages) {
            switch (image.getName()) {
                case "auramDraconicToolImage" -> color = image;
                case "auramDraconicDepthImage" -> depth = image;
            }
        }
        if (color == null || depth == null) {
            return;
        }

        // Iris owns this framebuffer and updates its scene depth attachment when resized.
        auram$toolTarget = renderTargets.createFramebufferWritingToMain(new int[]{0});
        auram$toolTarget.addColorAttachment(0, color.getId());
        auram$toolTarget.addColorAttachment(1, depth.getId());
        auram$toolTarget.drawBuffers(new int[]{0, 1});

        var options = programs.getPack().getShaderPackOptions().getOptionValues();
        auram$toolTaa = options.getBooleanValue("TAA").orElse(true);
        if (auram$toolTaa && options.getBooleanValue("TAA_UPSCALING").orElse(false)) {
            auram$toolScale = Float.parseFloat(options.getStringValue("SCALE_FACTOR").orElse("0.75"));
        }
    }

    @Override
    public boolean auram$bindToolTarget() {
        if (auram$toolTarget == null) {
            return false;
        }
        auram$toolTarget.bindAsDrawBuffer();
        return true;
    }

    @Override
    public Matrix4f auram$toolProjection(Matrix4f projection) {
        if (auram$toolTarget == null || !auram$toolTaa) {
            return projection;
        }

        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        int phase = SystemTimeUniforms.COUNTER.getAsInt() & 7;
        float offsetX = auram$toolScale - 1.0f + AURAM_JITTER_X[phase] / (8.0f * target.width);
        float offsetY = auram$toolScale - 1.0f + AURAM_JITTER_Y[phase] / (8.0f * target.height);
        return new Matrix4f().translation(offsetX, offsetY, 0.0f)
                .scale(auram$toolScale, auram$toolScale, 1.0f).mul(projection);
    }
}

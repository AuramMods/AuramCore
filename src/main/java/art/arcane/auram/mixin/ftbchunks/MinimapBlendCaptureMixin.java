package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.client.MinimapWidgetCache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GlStateManager.class)
abstract class MinimapBlendCaptureMixin {
    // Over transparent pixels, stock RGB blending produces premultiplied color.
    // Accumulate coverage separately so replay preserves translucent edges and text.
    @WrapMethod(method = "_blendFunc")
    private static void auram$accumulateWidgetAlpha(int source, int destination, Operation<Void> blend) {
        if (MinimapWidgetCache.capturingAlpha() && source == GL14.GL_SRC_ALPHA
                && destination == GL14.GL_ONE_MINUS_SRC_ALPHA) {
            GlStateManager._blendFuncSeparate(source, destination, GL14.GL_ONE, GL14.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            blend.call(source, destination);
        }
    }

    @WrapMethod(method = "_blendFuncSeparate")
    private static void auram$preserveWidgetAlpha(int source, int destination, int alphaSource, int alphaDestination,
                                                  Operation<Void> blend) {
        if (MinimapWidgetCache.capturingAlpha() && source == GL14.GL_SRC_ALPHA
                && destination == GL14.GL_ONE_MINUS_SRC_ALPHA) {
            blend.call(source, destination, GL14.GL_ONE, GL14.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            blend.call(source, destination, alphaSource, alphaDestination);
        }
    }
}

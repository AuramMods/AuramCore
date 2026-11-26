package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.Auram;
import art.arcane.auram.client.MinimapFeather;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.Tesselator;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import dev.ftb.mods.ftbchunks.client.FTBChunksClientConfig;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(value = FTBChunksClient.class, remap = false)
abstract class MinimapOverlayMixin {
    @Unique
    private static final ResourceLocation AURAM$PLAYER_DOT =
            new ResourceLocation(Auram.MODID, "textures/gui/minimap_player_dot.png");

    @WrapOperation(method = "renderHud", slice = @Slice(
            from = @At(value = "FIELD", target = "Ldev/ftb/mods/ftbchunks/client/FTBChunksClient;CIRCLE_MASK:Lnet/minecraft/resources/ResourceLocation;"),
            to = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(II)V")),
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/Tesselator;end()V", remap = true))
    private void auram$skipCircularDepthMask(Tesselator tessellator, Operation<Void> draw) {
        auram$skipCircularBatch(tessellator, draw);
    }

    @WrapOperation(method = "renderHud", slice = @Slice(
            from = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(II)V"),
            to = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;disableDepthTest()V")),
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/Tesselator;end()V", remap = true))
    private void auram$featherCircularTerrain(Tesselator tessellator, Operation<Void> draw,
                                             @Local(name = "offX") float centerU,
                                             @Local(name = "offZ") float centerV,
                                             @Local(name = "zws") float uvRadius) {
        if (auram$useFeather()) {
            // The terrain quad's UVs already carry its moving center and zoom. The shader clips
            // the circle itself, so this draw does not need FTB's depth-only hard mask.
            RenderSystem.disableDepthTest();
            // Other HUD draws can change blending without invalidating the cached shader blend mode.
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.defaultBlendFunc();
            MinimapFeather.use(centerU, centerV, uvRadius);
        }
        draw.call(tessellator);
    }

    @WrapOperation(method = "renderHud", slice = @Slice(
            from = @At(value = "FIELD", target = "Ldev/ftb/mods/ftbchunks/client/FTBChunksClient;CIRCLE_BORDER:Lnet/minecraft/resources/ResourceLocation;"),
            to = @At(value = "FIELD", target = "Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;DEBUG_LINES:Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;")),
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/Tesselator;end()V", remap = true))
    private void auram$skipCircularBorder(Tesselator tessellator, Operation<Void> draw) {
        auram$skipCircularBatch(tessellator, draw);
    }

    @Unique
    private static boolean auram$useFeather() {
        return !FTBChunksClientConfig.SQUARE_MINIMAP.get() && MinimapFeather.isLoaded();
    }

    @Unique
    private static void auram$skipCircularBatch(Tesselator tessellator, Operation<Void> draw) {
        if (auram$useFeather()) {
            tessellator.getBuilder().end().release();
        } else {
            draw.call(tessellator);
        }
    }

    @WrapOperation(method = "renderHud", slice = @Slice(
            from = @At(value = "FIELD", target = "Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;DEBUG_LINES:Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;", remap = false),
            to = @At(value = "FIELD", target = "Ldev/ftb/mods/ftbchunks/client/FTBChunksClientConfig;MINIMAP_COMPASS:Ldev/ftb/mods/ftblibrary/snbt/config/BooleanValue;", remap = false)),
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/Tesselator;end()V", remap = true))
    private void auram$discardCompassReticle(Tesselator tessellator, Operation<Void> draw) {
        // Release the reticle batch without uploading it so the next minimap draw can reuse the builder.
        tessellator.getBuilder().end().release();
    }

    @ModifyExpressionValue(method = "renderHud", at = @At(value = "FIELD", target =
            "Ldev/ftb/mods/ftbchunks/client/FTBChunksClient;PLAYER:Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation auram$replaceMinimapPlayerTexture(ResourceLocation original) {
        return AURAM$PLAYER_DOT;
    }
}

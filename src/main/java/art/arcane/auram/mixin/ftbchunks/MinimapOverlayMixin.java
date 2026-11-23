package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.Auram;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.Tesselator;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import net.minecraft.resources.ResourceLocation;
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

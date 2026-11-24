package art.arcane.auram.mixin.tconstruct;

import art.arcane.auram.tconstruct.client.TinkersFluidLightTexture;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import slimeknights.mantle.client.render.RenderItem;
import slimeknights.tconstruct.smeltery.client.render.CastingBlockEntityRenderer;
import slimeknights.tconstruct.smeltery.client.util.CastingItemRenderTypeBuffer;

@Mixin(value = CastingBlockEntityRenderer.class, remap = false)
abstract class BlissCastingPreviewMixin {
    // Written into Iris's per-vertex block-entity ID. Keep in sync with all_solid.vsh.
    private static final int CASTING_PREVIEW = 32760;

    @WrapOperation(method = "render(Lslimeknights/tconstruct/smeltery/block/entity/CastingBlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "INVOKE", ordinal = 1, remap = false, target =
                    "Lslimeknights/mantle/client/render/RenderingHelper;renderItem(Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/item/ItemStack;"
                            + "Lslimeknights/mantle/client/render/RenderItem;I)V"), remap = false)
    private void auram$markCoolingPreview(PoseStack pose, MultiBufferSource buffers, ItemStack stack,
                                          RenderItem model, int light, Operation<Void> render) {
        if (!(buffers instanceof CastingItemRenderTypeBuffer) || !TinkersFluidLightTexture.blissActive()) {
            render.call(pose, buffers, stack, model, light);
            return;
        }

        var state = CapturedRenderingState.INSTANCE;
        int previous = state.getCurrentRenderedBlockEntity();
        // Oculus gives block items their own ID when ItemRenderer starts.
        // Attach the casting ID at buffer acquisition so it reaches the actual vertices.
        MultiBufferSource preview = type -> {
            state.setCurrentBlockEntity(CASTING_PREVIEW);
            return buffers.getBuffer(type);
        };
        try {
            render.call(pose, preview, stack, model, light);
        } finally {
            state.setCurrentBlockEntity(previous);
        }
    }
}

package art.arcane.auram.mixin.botania;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "vazkii.botania.client.render.block_entity.ManaPoolBlockEntityRenderer", remap = false)
public abstract class ManaPoolRendererMixin {
    @ModifyExpressionValue(method = "render(Lvazkii/botania/common/block/block_entity/mana/ManaPoolBlockEntity;"
            + "FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "FIELD", target = "Lvazkii/botania/client/core/helper/RenderHelper;"
                    + "MANA_POOL_WATER:Lnet/minecraft/client/renderer/RenderType;", remap = false))
    private RenderType auram$useShaderPackManaLayer(RenderType original) {
        if (!IrisApi.getInstance().isShaderPackInUse()) {
            return original;
        }

        // The text layer has the same position/color/UV/lightmap format and an Oculus shader override.
        return RenderType.text(InventoryMenu.BLOCK_ATLAS);
    }
}

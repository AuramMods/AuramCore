package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.DerenderClientSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.brandon3055.draconicevolution.client.render.tile.RenderTileEnergyCore", remap = false)
public abstract class DraconicCoreRenderProbeMixin {
    @Inject(method = "render(Lcom/brandon3055/draconicevolution/blocks/tileentity/TileEnergyCore;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("RETURN"), remap = false)
    private void auram$energyCore(CallbackInfo ci) {
        DerenderClientSmoke.energyCoreRendered();
    }
}

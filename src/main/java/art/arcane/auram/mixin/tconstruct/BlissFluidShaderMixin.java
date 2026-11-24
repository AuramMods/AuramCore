package art.arcane.auram.mixin.tconstruct;

import art.arcane.auram.tconstruct.client.TinkersFluidLightTexture;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "slimeknights.mantle.client.render.MantleShaders", remap = false)
abstract class BlissFluidShaderMixin {
    @Inject(method = "getConfiguredFluidShader", at = @At("HEAD"), cancellable = true)
    private static void auram$selectFluidShader(CallbackInfoReturnable<ShaderInstance> callback) {
        if (ModList.get().isLoaded("oculus") && TinkersFluidLightTexture.blissActive()) {
            // Both Mantle and the smeltery submit the block vertex format.
            callback.setReturnValue(GameRenderer.getRendertypeTranslucentShader());
        }
    }
}

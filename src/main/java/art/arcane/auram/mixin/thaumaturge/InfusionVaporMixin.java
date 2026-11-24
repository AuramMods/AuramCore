package art.arcane.auram.mixin.thaumaturge;

import art.arcane.auram.vapor.ThaumaturgeVapor;
import com.leclowndu93150.thaumaturge.content.infusion.BlockEntityInfusionMatrix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockEntityInfusionMatrix.class, remap = false)
abstract class InfusionVaporMixin {
    @Inject(method = "tickServer", at = @At("RETURN"))
    private void auram$vapor(CallbackInfo ci) {
        var matrix = (BlockEntityInfusionMatrix) (Object) this;
        if (matrix.isActive()) ThaumaturgeVapor.infusion(matrix, matrix.stability());
    }
}

package art.arcane.auram.mixin.thaumaturge;

import art.arcane.auram.vapor.ThaumaturgeVapor;
import com.leclowndu93150.thaumaturge.content.essentia.BlockEntityCentrifuge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockEntityCentrifuge.class, remap = false)
abstract class CentrifugeVaporMixin {
    @Inject(method = "advanceProcess", at = @At("RETURN"))
    private void auram$vapor(CallbackInfo callback) {
        ThaumaturgeVapor.machine((BlockEntityCentrifuge) (Object) this, 1);
    }
}

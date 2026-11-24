package art.arcane.auram.mixin.thaumaturge;

import art.arcane.auram.vapor.ThaumaturgeVapor;
import com.leclowndu93150.thaumaturge.content.essentia.crystalizer.BlockEntityEssentiaCrystalizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockEntityEssentiaCrystalizer.class, remap = false)
abstract class CrystalizerVaporMixin {
    @Inject(method = "advance", at = @At("RETURN"))
    private void auram$vapor(CallbackInfo callback) {
        ThaumaturgeVapor.machine((BlockEntityEssentiaCrystalizer) (Object) this, 5);
    }
}

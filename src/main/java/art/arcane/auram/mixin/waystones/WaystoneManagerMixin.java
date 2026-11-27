package art.arcane.auram.mixin.waystones;

import art.arcane.auram.waystone.WaystoneDestinations;
import net.blay09.mods.waystones.api.IWaystone;
import net.blay09.mods.waystones.core.WaystoneManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WaystoneManager.class, remap = false)
abstract class WaystoneManagerMixin {
    @Inject(method = {"addWaystone", "removeWaystone", "updateWaystone"}, at = @At("RETURN"))
    private void auram$waystonesChanged(IWaystone stone, CallbackInfo ci) {
        if ((Object) this != WaystoneManager.get(null) && WaystoneDestinations.isPlayerWaystone(stone)) {
            WaystoneDestinations.changed();
        }
    }
}

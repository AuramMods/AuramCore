package art.arcane.auram.mixin.ftbessentials;

import art.arcane.auram.waystone.DestinationMapSync;
import dev.ftb.mods.ftbessentials.util.SavedTeleportManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SavedTeleportManager.class, remap = false)
abstract class SavedTeleportManagerSyncMixin {
    @Inject(method = "addDestination", at = @At("RETURN"))
    private void auram$destinationAdded(CallbackInfo ci) {
        DestinationMapSync.changed();
    }

    @Inject(method = "deleteDestination", at = @At("RETURN"))
    private void auram$destinationDeleted(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) DestinationMapSync.changed();
    }
}

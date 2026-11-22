package art.arcane.auram.mixin.forge;

import net.minecraftforge.network.ConfigSync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = ConfigSync.class, remap = false)
public abstract class LocalConfigSyncMixin {
    @Inject(method = "syncConfigs", at = @At("HEAD"), cancellable = true)
    private void auram$skipInProcessConfigPackets(boolean local, CallbackInfoReturnable<List<?>> callback) {
        // The integrated client shares the server's config instances. Forge already ignores these packets there.
        if (local) callback.setReturnValue(List.of());
    }
}

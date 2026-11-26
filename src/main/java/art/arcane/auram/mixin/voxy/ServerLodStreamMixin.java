package art.arcane.auram.mixin.voxy;

import art.arcane.auram.compat.voxy.LandingChunkDelivery;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.cortex.voxy.server.ServerLodStreams", remap = false)
public abstract class ServerLodStreamMixin {
    @Inject(method = "sendNext", at = @At("HEAD"), cancellable = true)
    private void auram$deferLodsUntilLanding(ServerPlayer player, @Coerce Object stream, long now, boolean mayQuery,
                                          CallbackInfoReturnable<Boolean> ci) {
        if (player.connection instanceof LandingChunkDelivery delivery && delivery.auram$landingChunksPending()) {
            ci.setReturnValue(false);
        }
    }
}

package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.DistantHorizonsSmoke;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundKeepAlivePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class DhClientKeepAliveMixin {
    @Shadow public abstract void send(Packet<?> packet);

    @Inject(method = "handleKeepAlive", at = @At("HEAD"), cancellable = true)
    private void auram$answerDuringRecipeIndexing(ClientboundKeepAlivePacket packet, CallbackInfo ci) {
        if (DistantHorizonsSmoke.CLIENT) {
            send(new ServerboundKeepAlivePacket(packet.getId()));
            ci.cancel();
        }
    }
}

package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.FieldNetworkSmoke;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class FieldPacketProbeMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"))
    private void auramSmoke$countFieldPayload(Packet<?> packet, PacketSendListener listener, CallbackInfo callback) {
        if (!FieldNetworkSmoke.ENABLED || !(packet instanceof ClientboundCustomPayloadPacket payload)) return;
        var channel = payload.getIdentifier();
        if (!channel.getNamespace().equals("auram")) return;
        switch (channel.getPath()) {
            case "vapor", "wind", "light", "rf_flow", "fields" -> {
                Connection connection = (Connection) (Object) this;
                if (!connection.isMemoryConnection()
                        && connection.getPacketListener() instanceof ServerGamePacketListenerImpl game) {
                    FieldNetworkSmoke.sent(game.player.getGameProfile().getName(), channel.getPath(),
                            ((FieldPayloadAccess) payload).auramSmoke$payload().readableBytes());
                }
            }
            default -> {}
        }
    }
}

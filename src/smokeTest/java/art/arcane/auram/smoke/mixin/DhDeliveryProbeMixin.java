package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.DistantHorizonsSmoke;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class DhDeliveryProbeMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("RETURN"))
    private void auram$chunkSent(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        // ChunkSending bypasses ServerPlayer.trackChunk and sends from the player tick.
        if (DistantHorizonsSmoke.SERVER && packet instanceof ClientboundLevelChunkWithLightPacket chunk
                && player.serverLevel().getServer().isSameThread()) {
            DistantHorizonsSmoke.delivered(player, new ChunkPos(chunk.getX(), chunk.getZ()));
        }
    }
}

package art.arcane.auram.mixin.voxy;

import art.arcane.auram.compat.voxy.LandingChunkDelivery;

import net.minecraft.core.SectionPos;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class LandingChunkDeliveryMixin implements LandingChunkDelivery {
    @Unique private static final int AURAM_LANDING_CHUNK_MASK = 0x1ff;
    @Unique private static final int AURAM_LANDING_LIMIT_TICKS = 100;
    @Unique private static final boolean AURAM_LANDING_TRACE = Boolean.getBoolean("auram.trace.voxyLanding");

    @Shadow public ServerPlayer player;
    @Unique private boolean auram$nextPositionIsLanding;
    @Unique private int auram$landingCenterX;
    @Unique private int auram$landingCenterZ;
    @Unique private int auram$landingStarted;
    @Unique private int auram$landingChunks;

    @Inject(method = "teleport(DDDFFLjava/util/Set;)V", at = @At("HEAD"))
    private void auram$landingPosition(double x, double y, double z, float yaw, float pitch,
                                       Set<RelativeMovement> relative, CallbackInfo ci) {
        int chunkX = SectionPos.posToSectionCoord(x);
        int chunkZ = SectionPos.posToSectionCoord(z);
        var last = player.getLastSectionPos();
        int viewDistance = player.serverLevel().getServer().getPlayerList().getViewDistance();
        int far = Math.max(2, viewDistance - 2);
        if (auram$nextPositionIsLanding || Math.abs(chunkX - last.x()) >= far || Math.abs(chunkZ - last.z()) >= far) {
            auram$nextPositionIsLanding = false;
            auram$landingCenterX = chunkX;
            auram$landingCenterZ = chunkZ;
            auram$landingStarted = player.serverLevel().getServer().getTickCount();
            auram$landingChunks = AURAM_LANDING_CHUNK_MASK;
            if (AURAM_LANDING_TRACE) {
                System.out.println("AURAM_VOXY_LANDING begin dimension=" + player.serverLevel().dimension().location()
                        + " x=" + chunkX + " z=" + chunkZ + " tick=" + auram$landingStarted);
            }
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("RETURN"))
    private void auram$landingPacketSent(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if (packet instanceof ClientboundLoginPacket || packet instanceof ClientboundRespawnPacket) {
            auram$nextPositionIsLanding = true;
        } else if (auram$landingChunks != 0 && packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            int x = chunk.getX() - auram$landingCenterX + 1;
            int z = chunk.getZ() - auram$landingCenterZ + 1;
            if (x >= 0 && x < 3 && z >= 0 && z < 3) {
                auram$landingChunks &= ~(1 << (x + z * 3));
                if (auram$landingChunks == 0 && AURAM_LANDING_TRACE) auram$traceRelease("sent");
            }
        }
    }

    @Override
    public boolean auram$landingChunksPending() {
        if (auram$landingChunks != 0 && player.serverLevel().getServer().getTickCount() - auram$landingStarted
                >= AURAM_LANDING_LIMIT_TICKS) {
            auram$landingChunks = 0;
            if (AURAM_LANDING_TRACE) auram$traceRelease("window-ended");
        }
        return auram$landingChunks != 0;
    }

    @Unique
    private void auram$traceRelease(String reason) {
        int elapsed = player.serverLevel().getServer().getTickCount() - auram$landingStarted;
        System.out.println("AURAM_VOXY_LANDING " + reason + " x=" + auram$landingCenterX + " z=" + auram$landingCenterZ
                + " ticks=" + elapsed);
    }
}

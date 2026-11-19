package art.arcane.auram.mixin.minecraft;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Set;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerMovementReadinessMixin {
    @Unique private static final int MAX_BUFFERED_PACKETS = 64;
    @Unique private static final int MAX_DELAY_TICKS = 60;
    @Unique private static final int PACKETS_PER_REPLAY = 4;

    @Shadow public ServerPlayer player;
    @Shadow private int tickCount;
    @Shadow private int receivedMovePacketCount;
    @Shadow private Vec3 awaitingPositionFromClient;
    @Shadow private double lastGoodX;
    @Shadow private double lastGoodZ;
    @Shadow public abstract void handleMovePlayer(ServerboundMovePlayerPacket packet);
    @Shadow public abstract void teleport(double x, double y, double z, float yaw, float pitch);
    @Shadow private static double clampHorizontal(double value) { throw new AssertionError(); }

    @Unique private ArrayDeque<ServerboundMovePlayerPacket> auram$movement;
    @Unique private ServerPlayer auram$bufferedPlayer;
    @Unique private ServerLevel auram$bufferedLevel;
    @Unique private Vec3 auram$expectedPosition;
    @Unique private int auram$startedTick;
    @Unique private boolean auram$replaying;

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerPlayer;getBoundingBox()Lnet/minecraft/world/phys/AABB;"), cancellable = true)
    private void auram$waitForWatchedDestination(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        // This point follows vanilla's packet, teleport, passenger, and speed checks.
        // Forge's position and collision handling still runs on replay.
        boolean pending = auram$movementChunksPending(packet);
        if (!pending && (auram$replaying || auram$movement == null)) return;
        if (auram$movement == null) {
            auram$movement = new ArrayDeque<>();
            auram$bufferedPlayer = player;
            auram$bufferedLevel = player.serverLevel();
            auram$expectedPosition = player.position();
            auram$startedTick = tickCount;
        }
        if (auram$movement.size() == MAX_BUFFERED_PACKETS) {
            auram$reconcilePosition();
        } else if (auram$replaying) {
            auram$movement.addFirst(packet);
        } else {
            auram$movement.addLast(packet);
        }
        // This packet will be counted by vanilla when it actually moves the player.
        receivedMovePacketCount--;
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void auram$continueMovement(CallbackInfo ci) {
        var buffered = auram$movement;
        if (buffered == null) return;
        if (player != auram$bufferedPlayer || player.serverLevel() != auram$bufferedLevel || player.isRemoved()
                || player.isPassenger() || player.isSleeping() || player.isDeadOrDying() || player.wonGame
                || awaitingPositionFromClient != null) {
            auram$clearMovement();
            return;
        }
        if (!player.position().equals(auram$expectedPosition) || tickCount - auram$startedTick >= MAX_DELAY_TICKS) {
            auram$reconcilePosition();
            return;
        }
        for (int count = 0; count < PACKETS_PER_REPLAY && auram$movement == buffered; count++) {
            var next = buffered.peekFirst();
            if (next == null) {
                auram$clearMovement();
                return;
            }
            if (auram$movementChunksPending(next)) return;
            buffered.removeFirst();
            auram$replaying = true;
            try {
                handleMovePlayer(next);
            } finally {
                auram$replaying = false;
            }
            if (auram$movement == buffered && buffered.peekFirst() != next) {
                auram$expectedPosition = player.position();
                auram$startedTick = tickCount;
            }
        }
        if (buffered.isEmpty() && auram$movement == buffered) auram$clearMovement();
    }

    @Inject(method = "teleport(DDDFFLjava/util/Set;)V", at = @At("HEAD"))
    private void auram$forgetBeforeTeleport(double x, double y, double z, float yaw, float pitch,
                                           Set<?> relativeMovements, CallbackInfo ci) {
        auram$clearMovement();
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void auram$forgetOnDisconnect(Component reason, CallbackInfo ci) {
        auram$clearMovement();
    }

    @Unique
    private boolean auram$movementChunksPending(ServerboundMovePlayerPacket packet) {
        if (!player.isAddedToWorld() || !packet.hasPosition()) return false;
        double x = clampHorizontal(packet.getX(player.getX()));
        double z = clampHorizontal(packet.getZ(player.getZ()));
        var current = player.chunkPosition();
        var chunks = player.serverLevel().getChunkSource();
        if (auram$pendingChunk(chunks, current, Mth.floor(x) >> 4, Mth.floor(z) >> 4)) return true;
        if (player.noPhysics) return false;

        // BlockCollisions reads one block beyond the swept box, even before the center crosses a border.
        // Include both Entity.move's last-good delta and the requested final bounding box.
        var box = player.getBoundingBox();
        double minX = Math.min(0, Math.min(x - lastGoodX, x - player.getX()));
        double maxX = Math.max(0, Math.max(x - lastGoodX, x - player.getX()));
        double minZ = Math.min(0, Math.min(z - lastGoodZ, z - player.getZ()));
        double maxZ = Math.max(0, Math.max(z - lastGoodZ, z - player.getZ()));
        int firstX = Mth.floor(box.minX + minX - 1.0000001) >> 4;
        int lastX = Mth.floor(box.maxX + maxX + 1.0000001) >> 4;
        int firstZ = Mth.floor(box.minZ + minZ - 1.0000001) >> 4;
        int lastZ = Mth.floor(box.maxZ + maxZ + 1.0000001) >> 4;
        if (lastX - firstX > 2 || lastZ - firstZ > 2) return false;
        for (int chunkX = firstX; chunkX <= lastX; chunkX++) {
            for (int chunkZ = firstZ; chunkZ <= lastZ; chunkZ++) {
                if (auram$pendingChunk(chunks, current, chunkX, chunkZ)) return true;
            }
        }
        return false;
    }

    @Unique
    private static boolean auram$pendingChunk(ServerChunkCache chunks, ChunkPos current, int x, int z) {
        // Defer on an existing FULL ticket and leave absent chunks to the ordinary handler.
        return (current.x != x || current.z != z) && chunks.hasChunk(x, z) && chunks.getChunkNow(x, z) == null;
    }

    @Unique
    private void auram$reconcilePosition() {
        auram$clearMovement();
        teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    @Unique
    private void auram$clearMovement() {
        auram$movement = null;
        auram$bufferedPlayer = null;
        auram$bufferedLevel = null;
        auram$expectedPosition = null;
    }
}

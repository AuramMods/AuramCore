package art.arcane.auram.mixin.dimdoors;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerGamePacketListenerImpl.class, priority = 1500)
public abstract class PlayerCollisionMixin {
    @Unique private static final String DIM_DOORS = "org.dimdev.dimdoors.mixin.ServerPlayNetworkHandlerMixin";

    @Shadow public ServerPlayer player;
    @Shadow private double lastGoodX;
    @Shadow private double lastGoodY;
    @Shadow private double lastGoodZ;
    @Shadow private int awaitingTeleport;

    @Unique private PendingCollision auram$pendingCollision;
    @Unique private Vec3 auram$replayedMovement;

    @TargetHandler(mixin = DIM_DOORS, name = "checkBlockCollision")
    @WrapMethod(method = "@MixinSquared:Handler", remap = false)
    private void auram$avoidWaitingForOverlap(ServerboundMovePlayerPacket packet, CallbackInfo ci,
                                               Operation<Void> runCollision) {
        auram$pendingCollision = null;
        ServerLevel level = player.serverLevel();
        AABB bounds = player.getBoundingBox();
        // Match DimDoors' inclusive, epsilon-inset block bounds.
        BlockPos min = BlockPos.containing(bounds.minX + 1.0E-7, bounds.minY + 1.0E-7, bounds.minZ + 1.0E-7);
        BlockPos max = BlockPos.containing(bounds.maxX - 1.0E-7, bounds.maxY - 1.0E-7, bounds.maxZ - 1.0E-7);
        if (!level.hasChunksAt(min, max)) return;

        if (auram$chunksReady(level, min, max)) {
            runCollision.call(packet, ci);
        } else {
            Vec3 movement = player.position().subtract(lastGoodX, lastGoodY, lastGoodZ);
            auram$pendingCollision = new PendingCollision(player, level, bounds, awaitingTeleport, min, max,
                    movement, () -> runCollision.call(packet, ci));
        }
    }

    @TargetHandler(mixin = DIM_DOORS, name = "checkBlockCollision")
    @Redirect(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState auram$readReadyBlock(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return Blocks.VOID_AIR.defaultBlockState();
        LevelChunk chunk = ((ServerLevel) level).getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
    }

    @TargetHandler(mixin = DIM_DOORS, name = "checkBlockCollision")
    @Redirect(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;subtract(DDD)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 auram$readMovement(Vec3 position, double x, double y, double z) {
        return auram$replayedMovement == null ? position.subtract(x, y, z) : auram$replayedMovement;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void auram$retryCollision(CallbackInfo ci) {
        PendingCollision pending = auram$pendingCollision;
        if (pending == null) return;
        if (player != pending.player() || player.isRemoved() || player.serverLevel() != pending.level()
                || awaitingTeleport != pending.teleport() || !player.getBoundingBox().equals(pending.bounds())
                || !pending.level().hasChunksAt(pending.min(), pending.max())) {
            auram$pendingCollision = null;
            return;
        }
        if (!auram$chunksReady(pending.level(), pending.min(), pending.max())) return;

        auram$pendingCollision = null;
        auram$replayedMovement = pending.movement();
        try {
            pending.run().run();
        } finally {
            auram$replayedMovement = null;
        }
    }

    @Unique
    private static boolean auram$chunksReady(ServerLevel level, BlockPos min, BlockPos max) {
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    public record PendingCollision(ServerPlayer player, ServerLevel level, AABB bounds, int teleport,
                                    BlockPos min, BlockPos max, Vec3 movement, Runnable run) { }
}

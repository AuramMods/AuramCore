package art.arcane.auram.mixin.distanthorizons;

import art.arcane.auram.util.ForegroundChunkDemand;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(ChunkMap.class)
public abstract class ChunkMapDemandMixin implements ForegroundChunkDemand {
    @Shadow @Final ServerLevel level;
    @Shadow protected abstract ChunkHolder getVisibleChunkIfPresent(long pos);

    @Unique private static final int NEARBY_CHUNK_RADIUS = 2;
    @Unique private volatile boolean auram$sampleRequested;
    @Unique private volatile boolean auram$playerChunksPending;

    @Override
    public boolean auram$samplePlayerChunkDemand() {
        auram$sampleRequested = true;
        return auram$playerChunksPending;
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void auram$publishPlayerChunkDemand(BooleanSupplier haveTime, CallbackInfo ci) {
        if (auram$sampleRequested) {
            auram$sampleRequested = false;
            auram$playerChunksPending = auram$hasPendingPlayerChunk();
        }
    }

    @Unique
    private boolean auram$hasPendingPlayerChunk() {
        boolean spectatorsGenerate = level.getGameRules().getBoolean(GameRules.RULE_SPECTATORSGENERATECHUNKS);
        for (ServerPlayer player : level.players()) {
            if (player.isRemoved() || (player.isSpectator() && !spectatorsGenerate)) continue;
            // Limit the guard to nearby chunks. The outer view can keep prefetching during flight.
            var center = player.getLastSectionPos();
            for (int x = center.x() - NEARBY_CHUNK_RADIUS; x <= center.x() + NEARBY_CHUNK_RADIUS; x++) {
                for (int z = center.z() - NEARBY_CHUNK_RADIUS; z <= center.z() + NEARBY_CHUNK_RADIUS; z++) {
                    ChunkHolder holder = getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
                    if (holder == null || holder.getTickingChunk() == null) return true;
                }
            }
        }
        return false;
    }
}

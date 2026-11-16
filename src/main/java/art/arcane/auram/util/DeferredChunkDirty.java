package art.arcane.auram.util;

import art.arcane.auram.Auram;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.IdentityHashMap;
import java.util.Map;

/** Keeps Mekanism save notifications without waiting for chunks or adding tickets. */
@Mod.EventBusSubscriber(modid = Auram.MODID)
public final class DeferredChunkDirty {
    // Accessed on the server thread; level unload removes its pending requests.
    private static final Map<ServerLevel, LongSet> PENDING = new IdentityHashMap<>();

    private DeferredChunkDirty() { }

    public static void mark(ServerLevel level, BlockPos pos) {
        if (!level.getServer().isSameThread()) {
            BlockPos immutablePos = pos.immutable();
            level.getServer().execute(() -> mark(level, immutablePos));
            return;
        }
        if (!level.isInWorldBounds(pos)) {
            return;
        }

        int x = SectionPos.blockToSectionCoord(pos.getX());
        int z = SectionPos.blockToSectionCoord(pos.getZ());
        LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
        if (chunk != null) {
            chunk.setUnsaved(true);
        } else if (level.getChunkSource().hasChunk(x, z)) {
            // hasChunk() means a FULL ticket exists, not that its chunk is ready.
            // Only retain requests the original Mekanism loaded check accepted.
            PENDING.computeIfAbsent(level, ignored -> new LongOpenHashSet()).add(ChunkPos.asLong(x, z));
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        LongSet pending = PENDING.get(level);
        if (pending == null) {
            return;
        }
        LongIterator it = pending.iterator();
        while (it.hasNext()) {
            long pos = it.nextLong();
            int x = ChunkPos.getX(pos);
            int z = ChunkPos.getZ(pos);
            LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
            if (chunk != null) {
                chunk.setUnsaved(true);
                it.remove();
            } else if (!level.getChunkSource().hasChunk(x, z)) {
                // A cancelled load has no in-memory chunk to save.
                it.remove();
            }
        }
        if (pending.isEmpty()) {
            PENDING.remove(level);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        markPending(event);
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        markPending(event);
    }

    private static void markPending(ChunkEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        // Use the event's actual chunk; querying the level from a load event can
        // recurse into the incomplete FULL future we are deliberately avoiding.
        LongSet pending = PENDING.get(level);
        if (pending != null && pending.remove(chunk.getPos().toLong())) {
            chunk.setUnsaved(true);
            if (pending.isEmpty()) {
                PENDING.remove(level);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PENDING.remove(level);
        }
    }
}

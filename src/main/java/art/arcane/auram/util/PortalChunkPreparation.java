package art.arcane.auram.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** A read-only portal lookup that can resume on a later server tick. */
public final class PortalChunkPreparation {
    private static final ThreadLocal<PortalChunkPreparation> ACTIVE = new ThreadLocal<>();
    private static final AtomicInteger ACTIVE_LOOKUPS = new AtomicInteger();
    private static final TicketType<ChunkPos> TICKET = TicketType.create(
            "auram_portal_search", Comparator.comparingLong(ChunkPos::toLong), 5);
    private static final PauseLookup WAITING = new PauseLookup();
    private static final PauseLookup NEEDS_CREATION = new PauseLookup();

    private final ServerChunkCache chunks;
    private final BlockPos origin;
    private final Set<ChunkPos> requested = new HashSet<>();
    private ChunkPos pending;

    public PortalChunkPreparation(ServerChunkCache chunks, BlockPos origin) {
        this.chunks = chunks;
        this.origin = origin;
    }

    public static PortalChunkPreparation active() {
        // Ordinary FULL chunk reads never need to look into the thread-local
        // map when no server is preparing a portal destination.
        return ACTIVE_LOOKUPS.get() == 0 ? null : ACTIVE.get();
    }

    public boolean matches(ServerChunkCache chunks, BlockPos origin) {
        return this.chunks == chunks && this.origin.equals(origin);
    }

    public boolean isDestination(ServerChunkCache chunks) {
        return this.chunks == chunks;
    }

    public boolean ready(Runnable lookup) {
        // Keep requested chunks alive while another candidate or part of a portal is loading.
        requested.forEach(this::keepLoaded);
        if (pending != null && chunks.getChunkNow(pending.x, pending.z) == null) return false;
        pending = null;

        PortalChunkPreparation previous = active();
        ACTIVE_LOOKUPS.incrementAndGet();
        try {
            ACTIVE.set(this);
            lookup.run();
            return true;
        } catch (PauseLookup pause) {
            return pause == NEEDS_CREATION;
        } finally {
            try {
                restore(previous);
            } finally {
                ACTIVE_LOOKUPS.decrementAndGet();
            }
        }
    }

    public LevelChunk readChunk(int x, int z) {
        LevelChunk chunk = chunks.getChunkNow(x, z);
        if (chunk != null) return chunk;
        pending = new ChunkPos(x, z);
        requested.add(pending);
        keepLoaded(pending);
        throw WAITING;
    }

    public static void outsideLookup(Runnable action) {
        PortalChunkPreparation previous = active();
        if (previous == null) {
            action.run();
            return;
        }
        ACTIVE.remove();
        try {
            action.run();
        } finally {
            restore(previous);
        }
    }

    public static void finishBeforeCreation(ServerChunkCache chunks) {
        PortalChunkPreparation lookup = active();
        if (lookup != null && lookup.isDestination(chunks)) throw NEEDS_CREATION;
    }

    private void keepLoaded(ChunkPos pos) {
        chunks.addRegionTicket(TICKET, pos, 0, pos);
    }

    private static void restore(PortalChunkPreparation previous) {
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }

    private static final class PauseLookup extends RuntimeException {
        private PauseLookup() {
            super(null, null, false, false);
        }
    }
}

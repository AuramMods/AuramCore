package art.arcane.auram.util;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class DeferredRingSearch {
    public interface Access {
        Boolean auram$containsRingChunk(ConcentricRingsStructurePlacement placement, int x, int z);
    }

    // Vanilla searches 112 blocks in each direction from the center of the candidate chunk.
    private static final int SEARCH_RADIUS_IN_CHUNKS = 7;
    private final List<Candidate> candidates = new ArrayList<>();

    public CompletableFuture<ChunkPos> defer(int x, int z, Supplier<ChunkPos> search, Executor executor) {
        var candidate = new Candidate(x, z, search, executor);
        candidates.add(candidate);
        return candidate.result;
    }

    public boolean contains(int x, int z) {
        for (Candidate candidate : candidates) {
            if (!candidate.canReach(x, z)) continue;
            candidate.start();
            ChunkPos resolved = candidate.result.join();
            if (resolved.x == x && resolved.z == z) return true;
        }
        return false;
    }

    public void startAll() {
        candidates.forEach(Candidate::start);
    }

    private static final class Candidate {
        private final int x;
        private final int z;
        private final Executor executor;
        private final AtomicBoolean started = new AtomicBoolean();
        private final CompletableFuture<ChunkPos> result = new CompletableFuture<>();
        private Supplier<ChunkPos> search;

        private Candidate(int x, int z, Supplier<ChunkPos> search, Executor executor) {
            this.x = x;
            this.z = z;
            this.search = search;
            this.executor = executor;
        }

        private boolean canReach(int otherX, int otherZ) {
            return Math.abs((long) x - otherX) <= SEARCH_RADIUS_IN_CHUNKS
                    && Math.abs((long) z - otherZ) <= SEARCH_RADIUS_IN_CHUNKS;
        }

        private void start() {
            if (!started.compareAndSet(false, true)) return;
            Supplier<ChunkPos> task = search;
            search = null;
            try {
                CompletableFuture.supplyAsync(task, executor).whenComplete((position, failure) -> {
                    if (failure == null) result.complete(position);
                    else result.completeExceptionally(failure);
                });
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        }
    }
}

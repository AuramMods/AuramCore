package art.arcane.auram.map.client;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.function.IntPredicate;

/** First uploaded coverage in a region. Revisiting or rerasterizing coverage does not restart it. */
public final class MapTileFades {
    static final int CHUNKS_PER_AXIS = 32;
    static final int CHUNK_COUNT = CHUNKS_PER_AXIS * CHUNKS_PER_AXIS;
    private final BitSet discovered = new BitSet(CHUNK_COUNT);
    private final ArrayDeque<Upload> fading = new ArrayDeque<>();

    public synchronized void uploaded(IntPredicate covered, boolean animate, long now) {
        expire(now);
        BitSet newChunks = null;
        for (int index = discovered.nextClearBit(0); index < CHUNK_COUNT; index = discovered.nextClearBit(index + 1)) {
            if (!covered.test(index)) continue;
            discovered.set(index);
            if (animate) {
                if (newChunks == null) newChunks = new BitSet(CHUNK_COUNT);
                newChunks.set(index);
            }
        }
        if (newChunks != null) fading.addLast(new Upload(newChunks, now));
    }

    synchronized boolean fillAlphas(int[] alphas, long now) {
        expire(now);
        if (fading.isEmpty()) return false;
        Arrays.fill(alphas, 255);
        for (Upload upload : fading) {
            int alpha = (int) Math.round(255 * MapAnimation.easeOut(now - upload.time));
            for (int index = upload.chunks.nextSetBit(0); index >= 0; index = upload.chunks.nextSetBit(index + 1)) {
                alphas[index] = alpha;
            }
        }
        return true;
    }

    public synchronized void releaseImage() {
        fading.clear();
    }

    private void expire(long now) {
        while (!fading.isEmpty() && now - fading.getFirst().time >= MapAnimation.DURATION) {
            fading.removeFirst();
        }
    }

    private record Upload(BitSet chunks, long time) {}
}

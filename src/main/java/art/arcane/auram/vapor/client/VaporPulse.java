package art.arcane.auram.vapor.client;

import art.arcane.auram.field.VoxelGrid.Position;

import java.util.Arrays;

import static art.arcane.auram.field.VoxelGrid.BLOCKS;
import static art.arcane.auram.vapor.client.VaporVolume.*;

/** Rasterizes both backends' pulse inputs using the same fine-block paths. */
final class VaporPulse {
    private static final int PLANE = SIDE * SIDE;
    private final int[] pulseQueue = new int[CELLS];
    private final int[] pulseVisits = new int[CELLS];
    private int pulseId;
    private boolean[] open;

    @FunctionalInterface
    interface CellConsumer {
        void accept(int index, float amount);
    }

    void visit(VaporVolume.Source source, Position origin, boolean[] open, CellConsumer consumer) {
        this.open = open;
        int x = source.position().x() - origin.x();
        int y = source.position().y() - origin.y();
        int z = source.position().z() - origin.z();
        if (source.density() == 0 || !BLOCKS.contains(x, y, z) || !open[index(x, y, z)]) return;
        if (source.radius() == 0) {
            consumer.accept(index(x, y, z), source.density());
            return;
        }

        // Visit only cells connected to the pulse center within its radius.
        if (++pulseId == 0) {
            Arrays.fill(pulseVisits, 0);
            pulseId = 1;
        }
        int center = index(x, y, z);
        int tail = visit(center, 0);
        int radiusSquared = source.radius() * source.radius();
        for (int head = 0; head < tail; head++) {
            int offset = pulseQueue[head];
            int px = offset % SIDE;
            int py = offset / SIDE % SIDE;
            int pz = offset / PLANE;
            int dx = px - x, dy = py - y, dz = pz - z;
            int distanceSquared = dx * dx + dy * dy + dz * dz;
            float fade = 1.0f - (float) Math.sqrt(distanceSquared) / (source.radius() + 1);
            consumer.accept(offset, source.density() * fade * fade);
            if (px > 0 && distanceSquared - 2 * dx + 1 <= radiusSquared) tail = visit(offset - 1, tail);
            if (px + 1 < SIDE && distanceSquared + 2 * dx + 1 <= radiusSquared) tail = visit(offset + 1, tail);
            if (py > 0 && distanceSquared - 2 * dy + 1 <= radiusSquared) tail = visit(offset - SIDE, tail);
            if (py + 1 < SIDE && distanceSquared + 2 * dy + 1 <= radiusSquared) tail = visit(offset + SIDE, tail);
            if (pz > 0 && distanceSquared - 2 * dz + 1 <= radiusSquared) tail = visit(offset - PLANE, tail);
            if (pz + 1 < SIDE && distanceSquared + 2 * dz + 1 <= radiusSquared) tail = visit(offset + PLANE, tail);
        }
    }

    private int visit(int offset, int tail) {
        if (!open[offset] || pulseVisits[offset] == pulseId) return tail;
        pulseVisits[offset] = pulseId;
        pulseQueue[tail] = offset;
        return tail + 1;
    }

}

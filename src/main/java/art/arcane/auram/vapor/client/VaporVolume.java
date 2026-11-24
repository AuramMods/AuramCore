package art.arcane.auram.vapor.client;

import art.arcane.auram.field.VoxelOccupancy;
import art.arcane.auram.field.VoxelGrid.Bounds;
import art.arcane.auram.field.VoxelGrid.Position;

import java.util.Arrays;
import java.util.List;

import static art.arcane.auram.field.VoxelGrid.BLOCKS;

/** The worker-owned density field. Positions and bounds are in world blocks. */
final class VaporVolume {
    static final int SIDE = BLOCKS.side();
    static final int CELLS = BLOCKS.cells();
    private static final float MAX_DENSITY = 1.0f;
    private static final float RETENTION = 0.975f;
    private static final float MIN_DENSITY = 0.001f;
    private static final int PLANE = SIDE * SIDE;

    static final class Erosion {
        private static final int SHIFT = 3;
        private static final int SIDE_SAMPLES = SIDE >> SHIFT;
        static final Erosion CLEAR = new Erosion(null);
        private final float[] retention;

        private Erosion(float[] retention) {
            this.retention = retention;
        }

        @FunctionalInterface
        interface Lingering {
            float at(int x, int y, int z);
        }

        static Erosion sample(Lingering lingering) {
            float[] retention = new float[SIDE_SAMPLES * SIDE_SAMPLES * SIDE_SAMPLES];
            int halfCell = 1 << (SHIFT - 1);
            for (int z = 0; z < SIDE_SAMPLES; z++) {
                for (int y = 0; y < SIDE_SAMPLES; y++) {
                    for (int x = 0; x < SIDE_SAMPLES; x++) {
                        float value = lingering.at((x << SHIFT) + halfCell, (y << SHIFT) + halfCell, (z << SHIFT) + halfCell);
                        retention[index(x, y, z)] = retentionFor(value);
                    }
                }
            }
            return new Erosion(retention);
        }

        static float retentionFor(float lingering) {
            float clamped = Math.max(0.0f, Math.min(lingering, 1.0f));
            // A half-strength erosion rate doubles the original exponential decay time.
            return (float) Math.pow(RETENTION, 1.0f - clamped);
        }

        float at(int x, int y, int z) {
            return retention == null ? RETENTION : retention[index(x >> SHIFT, y >> SHIFT, z >> SHIFT)];
        }

        private static int index(int x, int y, int z) {
            return x + SIDE_SAMPLES * (y + SIDE_SAMPLES * z);
        }
    }

    record Source(Position position, float density, int radius) {
        Source {
            if (!Float.isFinite(density) || density < 0.0f || density > 1.0f || radius < 0 || radius > 12) {
                throw new IllegalArgumentException("Vapor density must be 0–1 and radius must be 0–12 blocks");
            }
        }
    }

    // Published arrays are private and never reused or mutated by the worker.
    static final class Snapshot {
        final Position origin;
        final Bounds bounds;
        private final byte[] density;
        private final boolean[] open;

        private Snapshot(Position origin, Bounds bounds, byte[] density, boolean[] open) {
            this.origin = origin;
            this.bounds = bounds;
            this.density = density;
            this.open = open;
        }

        int density(int index) {
            return Byte.toUnsignedInt(density[index]);
        }

        boolean open(int index) {
            return open[index];
        }
    }

    private final int[] pulseQueue = new int[CELLS];
    private final int[] pulseVisits = new int[CELLS];
    private int pulseId;
    private VoxelOccupancy occupancy;
    private boolean[] open = new boolean[CELLS];
    private float[] density = new float[CELLS];
    private float[] display = new float[CELLS];
    private float[] next = new float[CELLS];
    private Bounds active = Bounds.EMPTY;

    Snapshot step(VoxelOccupancy updated, Erosion erosion, List<Source> pulses, List<Source> emitters) {
        updateOccupancy(updated);
        if (!active.empty()) spread(erosion);
        for (Source pulse : pulses) inject(pulse);
        for (Source emitter : emitters) inject(emitter);
        if (active.empty()) return null;
        return capture();
    }

    private void spread(Erosion erosion) {
        Arrays.fill(next, 0.0f);
        Bounds area = active;
        // Reserve space for the density that already occupies each cell after erosion.
        for (int z = area.minZ(); z < area.maxZ(); z++) {
            for (int y = area.minY(); y < area.maxY(); y++) {
                for (int x = area.minX(); x < area.maxX(); x++) {
                    int index = index(x, y, z);
                    float amount = density[index] * erosion.at(x, y, z);
                    density[index] = next[index] = amount >= MIN_DENSITY ? amount : 0.0f;
                }
            }
        }
        // Upper cells can free space before lower cells try to rise into it.
        for (int y = area.maxY() - 1; y >= area.minY(); y--) {
            for (int z = area.minZ(); z < area.maxZ(); z++) {
                for (int x = area.minX(); x < area.maxX(); x++) {
                    int index = index(x, y, z);
                    float amount = density[index];
                    if (amount == 0.0f) continue;
                    if (y + 1 < SIDE) transfer(index, index + SIDE, amount * 0.36f);
                    if (y > 0) transfer(index, index - SIDE, amount * 0.025f);
                    if (x + 1 < SIDE) transfer(index, index + 1, amount * 0.09f);
                    if (x > 0) transfer(index, index - 1, amount * 0.09f);
                    if (z + 1 < SIDE) transfer(index, index + PLANE, amount * 0.09f);
                    if (z > 0) transfer(index, index - PLANE, amount * 0.09f);
                }
            }
        }
        float[] old = density;
        density = next;
        next = old;
        active = area.expand(SIDE);
    }

    private void transfer(int from, int to, float offered) {
        if (!open[to]) return;
        float accepted = acceptedTransfer(offered, next[to]);
        if (accepted == 0.0f) return;
        next[to] += accepted;
        next[from] -= accepted;
    }

    static float acceptedTransfer(float offered, float destination) {
        return Math.min(offered, Math.max(0.0f, MAX_DENSITY - destination));
    }

    private void inject(Source source) {
        Position origin = occupancy.origin();
        int x = source.position.x() - origin.x();
        int y = source.position.y() - origin.y();
        int z = source.position.z() - origin.z();
        if (source.density == 0.0f || !inside(x, y, z) || !open[index(x, y, z)]) return;
        if (source.radius == 0) {
            add(x, y, z, source.density);
            return;
        }

        // Visit only cells connected to the pulse center within its radius.
        if (++pulseId == 0) {
            Arrays.fill(pulseVisits, 0);
            pulseId = 1;
        }
        int center = index(x, y, z);
        int tail = visit(center, 0);
        int radiusSquared = source.radius * source.radius;
        for (int head = 0; head < tail; head++) {
            int offset = pulseQueue[head];
            int px = offset % SIDE;
            int py = offset / SIDE % SIDE;
            int pz = offset / PLANE;
            int dx = px - x, dy = py - y, dz = pz - z;
            int distanceSquared = dx * dx + dy * dy + dz * dz;
            float fade = 1.0f - (float) Math.sqrt(distanceSquared) / (source.radius + 1);
            add(px, py, pz, source.density * fade * fade);
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

    private void add(int x, int y, int z, float amount) {
        int index = index(x, y, z);
        density[index] = Math.min(MAX_DENSITY, density[index] + amount);
        active = active.include(x, y, z);
    }

    private Snapshot capture() {
        byte[] pixels = null;
        Bounds live = Bounds.EMPTY;
        Bounds visible = Bounds.EMPTY;
        for (int z = active.minZ(); z < active.maxZ(); z++) {
            for (int y = active.minY(); y < active.maxY(); y++) {
                for (int x = active.minX(); x < active.maxX(); x++) {
                    int index = index(x, y, z);
                    float target = density[index];
                    float previous = display[index];
                    // About 94% of a held increase becomes visible in one second at 20 Hz.
                    float value = previous + (target - previous) * (target > previous ? 0.13f : 0.25f);
                    display[index] = value < MIN_DENSITY ? 0.0f : value;
                    int packed = Math.round(display[index] * 255.0f);
                    if (packed != 0) {
                        if (pixels == null) pixels = new byte[CELLS];
                        pixels[index] = (byte) packed;
                        visible = visible.include(x, y, z);
                    }
                    if (display[index] != 0.0f || target >= MIN_DENSITY) live = live.include(x, y, z);
                }
            }
        }
        active = live;
        return pixels == null ? null : new Snapshot(occupancy.origin(), visible.expand(SIDE), pixels, open);
    }

    boolean[] updateOccupancy(VoxelOccupancy updated) {
        if (updated != occupancy) replaceOccupancy(updated);
        return open;
    }

    private void replaceOccupancy(VoxelOccupancy updated) {
        boolean[] newOpen = updated.openBlocks();
        Position origin = updated.origin();
        if (occupancy != null && !active.empty()) {
            Position previous = occupancy.origin();
            int moveX = previous.x() - origin.x();
            int moveY = previous.y() - origin.y();
            int moveZ = previous.z() - origin.z();
            boolean moved = !previous.equals(origin);
            float[] newDensity = moved ? new float[CELLS] : density;
            float[] newDisplay = moved ? new float[CELLS] : display;
            Bounds retained = Bounds.EMPTY;
            for (int z = active.minZ(); z < active.maxZ(); z++) {
                for (int y = active.minY(); y < active.maxY(); y++) {
                    for (int x = active.minX(); x < active.maxX(); x++) {
                        int px = x + moveX, py = y + moveY, pz = z + moveZ;
                        if (!inside(px, py, pz)) continue;
                        int oldIndex = index(x, y, z);
                        int newIndex = index(px, py, pz);
                        if (!newOpen[newIndex]) {
                            newDensity[newIndex] = 0.0f;
                            newDisplay[newIndex] = 0.0f;
                            continue;
                        }
                        if (density[oldIndex] == 0.0f && display[oldIndex] == 0.0f) continue;
                        newDensity[newIndex] = density[oldIndex];
                        newDisplay[newIndex] = display[oldIndex];
                        retained = retained.include(px, py, pz);
                    }
                }
            }
            density = newDensity;
            display = newDisplay;
            active = retained;
        }
        open = newOpen;
        occupancy = updated;
    }

    static int index(int x, int y, int z) {
        return BLOCKS.index(x, y, z);
    }

    private static boolean inside(int x, int y, int z) {
        return BLOCKS.contains(x, y, z);
    }
}

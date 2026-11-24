package art.arcane.auram.vapor.client;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/** The worker-owned density field. Positions and bounds are in world blocks. */
final class VaporVolume {
    static final int SIDE = 64;
    static final int SECTION = 16;
    static final int CELLS = SIDE * SIDE * SIDE;
    private static final float RETENTION = 0.975f;
    private static final float MIN_DENSITY = 0.001f;
    private static final int PLANE = SIDE * SIDE;

    record Position(int x, int y, int z) {
        static Position originAround(int x, int y, int z) {
            return originFor(Math.floorDiv(x, SECTION), Math.floorDiv(y, SECTION), Math.floorDiv(z, SECTION));
        }

        private static Position originFor(int x, int y, int z) {
            return new Position(x * SECTION - SIDE / 2, y * SECTION - SIDE / 2, z * SECTION - SIDE / 2);
        }
    }

    // A missing section has unknown occupancy and is treated as sealed.
    record SectionOccupancy(BitSet occupied) {
        SectionOccupancy {
            occupied = (BitSet) occupied.clone();
        }

        boolean blocked(int x, int y, int z) {
            return occupied.get(sectionIndex(x, y, z));
        }

        SectionOccupancy with(int x, int y, int z, boolean blocked) {
            int index = sectionIndex(x, y, z);
            if (occupied.get(index) == blocked) return this;
            BitSet updated = (BitSet) occupied.clone();
            updated.set(index, blocked);
            return new SectionOccupancy(updated);
        }

        @Override
        public BitSet occupied() {
            return (BitSet) occupied.clone();
        }
    }

    record Occupancy(Position origin, Map<Position, SectionOccupancy> sections) {
        Occupancy {
            sections = Map.copyOf(sections);
        }
    }

    record Source(Position position, float density, int radius) {
        Source {
            if (!Float.isFinite(density) || density < 0.0f || density > 1.0f || radius < 0 || radius > 12) {
                throw new IllegalArgumentException("Vapor density must be 0–1 and radius must be 0–12 blocks");
            }
        }
    }

    record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static final Bounds EMPTY = new Bounds(0, 0, 0, 0, 0, 0);

        boolean empty() {
            return minX == maxX;
        }

        Bounds include(int x, int y, int z) {
            if (empty()) return new Bounds(x, y, z, x + 1, y + 1, z + 1);
            if (x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ) return this;
            return new Bounds(Math.min(minX, x), Math.min(minY, y), Math.min(minZ, z),
                    Math.max(maxX, x + 1), Math.max(maxY, y + 1), Math.max(maxZ, z + 1));
        }

        Bounds expand() {
            return new Bounds(Math.max(0, minX - 1), Math.max(0, minY - 1), Math.max(0, minZ - 1),
                    Math.min(SIDE, maxX + 1), Math.min(SIDE, maxY + 1), Math.min(SIDE, maxZ + 1));
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
    private Occupancy occupancy;
    private boolean[] open = new boolean[CELLS];
    private float[] density = new float[CELLS];
    private float[] display = new float[CELLS];
    private float[] next = new float[CELLS];
    private Bounds active = Bounds.EMPTY;

    Snapshot step(Occupancy updated, List<Source> pulses, List<Source> emitters) {
        if (updated != occupancy) replaceOccupancy(updated);
        if (!active.empty()) spread();
        for (Source pulse : pulses) inject(pulse);
        for (Source emitter : emitters) inject(emitter);
        if (active.empty()) return null;
        return capture();
    }

    private void spread() {
        Arrays.fill(next, 0.0f);
        Bounds area = active;
        for (int z = area.minZ; z < area.maxZ; z++) {
            for (int y = area.minY; y < area.maxY; y++) {
                for (int x = area.minX; x < area.maxX; x++) {
                    int index = index(x, y, z);
                    float amount = density[index] * RETENTION;
                    if (amount < MIN_DENSITY) continue;
                    float outgoing = 0.0f;
                    if (y + 1 < SIDE) outgoing += transfer(index + SIDE, amount * 0.36f);
                    if (y > 0) outgoing += transfer(index - SIDE, amount * 0.025f);
                    if (x + 1 < SIDE) outgoing += transfer(index + 1, amount * 0.09f);
                    if (x > 0) outgoing += transfer(index - 1, amount * 0.09f);
                    if (z + 1 < SIDE) outgoing += transfer(index + PLANE, amount * 0.09f);
                    if (z > 0) outgoing += transfer(index - PLANE, amount * 0.09f);
                    // Shares aimed at blocked cells remain here and can spread sideways on later steps.
                    next[index] += amount - outgoing;
                }
            }
        }
        float[] old = density;
        density = next;
        next = old;
        active = area.expand();
    }

    private float transfer(int offset, float amount) {
        if (!open[offset]) return 0.0f;
        next[offset] += amount;
        return amount;
    }

    private void inject(Source source) {
        Position origin = occupancy.origin;
        int x = source.position.x - origin.x;
        int y = source.position.y - origin.y;
        int z = source.position.z - origin.z;
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
        density[index] = Math.min(1.0f, density[index] + amount);
        active = active.include(x, y, z);
    }

    private Snapshot capture() {
        byte[] pixels = null;
        Bounds live = Bounds.EMPTY;
        Bounds visible = Bounds.EMPTY;
        for (int z = active.minZ; z < active.maxZ; z++) {
            for (int y = active.minY; y < active.maxY; y++) {
                for (int x = active.minX; x < active.maxX; x++) {
                    int index = index(x, y, z);
                    float target = Math.min(1.0f, density[index]);
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
        return pixels == null ? null : new Snapshot(occupancy.origin, visible.expand(), pixels, open);
    }

    private void replaceOccupancy(Occupancy updated) {
        boolean[] newOpen = new boolean[CELLS];
        Position origin = updated.origin;
        for (var entry : updated.sections.entrySet()) {
            Position section = entry.getKey();
            int x0 = section.x * SECTION - origin.x;
            int y0 = section.y * SECTION - origin.y;
            int z0 = section.z * SECTION - origin.z;
            if (!inside(x0, y0, z0)) continue;
            for (int z = 0; z < SECTION; z++) {
                for (int y = 0; y < SECTION; y++) {
                    for (int x = 0; x < SECTION; x++) {
                        newOpen[index(x0 + x, y0 + y, z0 + z)] = !entry.getValue().blocked(x, y, z);
                    }
                }
            }
        }

        if (occupancy != null && !active.empty()) {
            Position previous = occupancy.origin;
            int moveX = previous.x - origin.x;
            int moveY = previous.y - origin.y;
            int moveZ = previous.z - origin.z;
            boolean moved = !previous.equals(origin);
            float[] newDensity = moved ? new float[CELLS] : density;
            float[] newDisplay = moved ? new float[CELLS] : display;
            Bounds retained = Bounds.EMPTY;
            for (int z = active.minZ; z < active.maxZ; z++) {
                for (int y = active.minY; y < active.maxY; y++) {
                    for (int x = active.minX; x < active.maxX; x++) {
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
        return x + SIDE * (y + SIDE * z);
    }

    static int sectionIndex(int x, int y, int z) {
        return x + SECTION * (y + SECTION * z);
    }

    private static boolean inside(int x, int y, int z) {
        return x >= 0 && x < SIDE && y >= 0 && y < SIDE && z >= 0 && z < SIDE;
    }
}

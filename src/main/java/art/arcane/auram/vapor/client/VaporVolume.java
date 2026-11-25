package art.arcane.auram.vapor.client;

import art.arcane.auram.field.VoxelOccupancy;
import art.arcane.auram.field.WindField;
import art.arcane.auram.field.VoxelGrid.Bounds;
import art.arcane.auram.field.VoxelGrid.Position;

import java.util.Arrays;
import java.util.List;

import static art.arcane.auram.field.VoxelGrid.VAPOR;

/** The worker-owned density field. Positions and bounds are in world blocks. */
final class VaporVolume {
    static final int SIDE = VAPOR.side();
    static final int CELLS = VAPOR.cells();
    private static final float MAX_DENSITY = 1.0f;
    private static final float RETENTION = 0.975f;
    private static final float MIN_DENSITY = 0.001f;
    private static final float UP = 0.36f;
    private static final float DOWN = 0.025f;
    private static final float SIDEWAYS = 0.09f;
    private static final float TRANSPORT_RATE = UP + DOWN + 4 * SIDEWAYS;
    private static final int PLANE = SIDE * SIDE;

    static final class Erosion {
        static final int SHIFT = 3;
        static final int SIDE_SAMPLES = ((SIDE - 1) >> SHIFT) + 1;
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
                        float value = lingering.at(Math.min(SIDE - 1, (x << SHIFT) + halfCell),
                                Math.min(SIDE - 1, (y << SHIFT) + halfCell), Math.min(SIDE - 1, (z << SHIFT) + halfCell));
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

        int density(int x, int y, int z) {
            return VAPOR.contains(x, y, z) ? density(index(x, y, z)) : 0;
        }

        Bounds boundsAt(Position reference) {
            return bounds.translateClipped(origin.x() - reference.x(), origin.y() - reference.y(),
                    origin.z() - reference.z(), SIDE);
        }

        boolean open(int index) {
            return open[index];
        }
    }

    @FunctionalInterface
    interface FlowMeter {
        void accept(int from, int to, float amount);
    }

    private final float[] windVector = new float[3];
    private final VaporPulse pulse = new VaporPulse();
    private VoxelOccupancy occupancy;
    private boolean[] open = new boolean[CELLS];
    private float[] density = new float[CELLS];
    private float[] display = new float[CELLS];
    private float[] next = new float[CELLS];
    private float[] shiftedDisplay;
    private Bounds active = Bounds.EMPTY;

    Snapshot step(VoxelOccupancy updated, Erosion erosion, List<Source> pulses, List<Source> emitters) {
        return step(updated, erosion, pulses, emitters, null, null);
    }

    Snapshot step(VoxelOccupancy updated, Erosion erosion, List<Source> pulses, List<Source> emitters,
                  WindField wind, FlowMeter meter) {
        updateOccupancy(updated);
        if (!active.empty()) spread(erosion, wind != null && wind.active() ? wind : null, meter);
        for (Source pulse : pulses) inject(pulse);
        for (Source emitter : emitters) inject(emitter);
        if (active.empty()) return null;
        return capture();
    }

    private void spread(Erosion erosion, WindField wind, FlowMeter meter) {
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
                    if (wind == null || !offerWind(index, x, y, z, amount, wind, meter)) {
                        offer(index, x, y, z, amount, 0, 0, 0, meter);
                    }
                }
            }
        }
        float[] old = density;
        density = next;
        next = old;
        active = area.expand(SIDE);
    }

    private boolean offerWind(int from, int x, int y, int z, float amount, WindField wind, FlowMeter meter) {
        wind.sample(x, y, z, windVector);
        float wx = windVector[0], wy = windVector[1], wz = windVector[2];
        float lengthSquared = wx * wx + wy * wy + wz * wz;
        if (lengthSquared < WindField.MIN_WIND * WindField.MIN_WIND) return false;

        float bias = Math.min(0.98f, 5.0f * (float) Math.sqrt(lengthSquared));
        float directed = amount * bias * (TRANSPORT_RATE + 0.24f * bias)
                / (Math.abs(wx) + Math.abs(wy) + Math.abs(wz));
        offer(from, x, y, z, amount * (1 - bias), wx * directed, wy * directed, wz * directed, meter);
        return true;
    }

    private void offer(int from, int x, int y, int z, float amount, float east, float up, float south, FlowMeter meter) {
        if (y + 1 < SIDE) transfer(from, from + SIDE, amount * UP + Math.max(0, up), meter);
        if (y > 0) transfer(from, from - SIDE, amount * DOWN + Math.max(0, -up), meter);
        if (x + 1 < SIDE) transfer(from, from + 1, amount * SIDEWAYS + Math.max(0, east), meter);
        if (x > 0) transfer(from, from - 1, amount * SIDEWAYS + Math.max(0, -east), meter);
        if (z + 1 < SIDE) transfer(from, from + PLANE, amount * SIDEWAYS + Math.max(0, south), meter);
        if (z > 0) transfer(from, from - PLANE, amount * SIDEWAYS + Math.max(0, -south), meter);
    }

    private void transfer(int from, int to, float offered, FlowMeter meter) {
        if (!open[to]) return;
        float accepted = acceptedTransfer(offered, next[to]);
        if (accepted == 0.0f) return;
        next[to] += accepted;
        next[from] -= accepted;
        if (meter != null) meter.accept(from, to, accepted);
    }

    static float acceptedTransfer(float offered, float destination) {
        return Math.min(offered, Math.max(0.0f, MAX_DENSITY - destination));
    }

    private void inject(Source source) {
        pulse.visit(source, occupancy.origin(), open, this::add);
    }

    private void add(int index, float amount) {
        int x = index % SIDE, y = index / SIDE % SIDE, z = index / PLANE;
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
            if (moved) {
                Arrays.fill(next, 0.0f);
                if (shiftedDisplay == null) shiftedDisplay = new float[CELLS];
                else Arrays.fill(shiftedDisplay, 0.0f);
            }
            float[] newDensity = moved ? next : density;
            float[] newDisplay = moved ? shiftedDisplay : display;
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
            if (moved) {
                next = density;
                shiftedDisplay = display;
                density = newDensity;
                display = newDisplay;
            }
            active = retained;
        }
        open = newOpen;
        occupancy = updated;
    }

    static int index(int x, int y, int z) {
        return VAPOR.index(x, y, z);
    }

    private static boolean inside(int x, int y, int z) {
        return VAPOR.contains(x, y, z);
    }
}

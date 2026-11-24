package art.arcane.auram.vapor.client;

import art.arcane.auram.field.VoxelGrid.Position;
import art.arcane.auram.field.WindField;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Arrays;

import static art.arcane.auram.field.VoxelGrid.BLOCKS;

/** Opt-in diagnostics for test scenes. All measurements are local to this client's window. */
final class FieldProbe implements VaporVolume.FlowMeter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int WINDOW_TICKS = 40;
    private final String backend;
    private final Position windPosition;
    private final Position inside;
    private final Position outside;
    private final long[] times = new long[WINDOW_TICKS];
    private int samples;
    private double netFlux;
    private int inletIndex = -1;
    private int outletIndex = -1;

    static FieldProbe create(String backend) {
        String text = System.getProperty("auram.fields.probe");
        if (text == null) return null;
        if (text.isBlank()) return new FieldProbe(backend, null, null, null);

        String[] parts = text.split(",");
        if (parts.length == 9) {
            try {
                return new FieldProbe(backend, position(parts, 0), position(parts, 3), position(parts, 6));
            } catch (NumberFormatException invalid) {
                LOGGER.warn("Invalid voxel field probe coordinates");
                return null;
            }
        }
        LOGGER.warn("The voxel field probe takes nine coordinates: Wind sample, outlet inside, outlet outside");
        return null;
    }

    private FieldProbe(String backend, Position windPosition, Position inside, Position outside) {
        this.backend = backend;
        this.windPosition = windPosition;
        this.inside = inside;
        this.outside = outside;
    }

    private static Position position(String[] parts, int start) {
        return new Position(Integer.parseInt(parts[start].trim()), Integer.parseInt(parts[start + 1].trim()),
                Integer.parseInt(parts[start + 2].trim()));
    }

    void positionOutlet(Position origin) {
        inletIndex = index(inside, origin);
        outletIndex = index(outside, origin);
    }

    Position windPosition() {
        return windPosition;
    }

    int inlet() {
        return inletIndex;
    }

    int outlet() {
        return outletIndex;
    }

    @Override
    public void accept(int from, int to, float amount) {
        if (from == inletIndex && to == outletIndex) netFlux += amount;
        if (from == outletIndex && to == inletIndex) netFlux -= amount;
    }

    void reportComputeFlux(double amount) {
        netFlux += amount;
    }

    boolean measure(long start) {
        times[samples++] = System.nanoTime() - start;
        return samples == times.length;
    }

    void log(WindField wind) {
        int node = wind == null || windPosition == null ? -1 : wind.topology().node(windPosition);
        if (node < 0) log(0, 0, 0);
        else log(wind.component(node, 0), wind.component(node, 1), wind.component(node, 2));
    }

    void log(float x, float y, float z) {
        Arrays.sort(times);
        LOGGER.info("Voxel field probe backend={} medianNs={} p95Ns={} fluxPerTick={} wind=({},{},{})",
                backend, times[WINDOW_TICKS / 2], times[WINDOW_TICKS * 95 / 100 - 1],
                netFlux / WINDOW_TICKS, x, y, z);
        samples = 0;
        netFlux = 0;
    }

    private static int index(Position world, Position origin) {
        if (world == null) return -1;
        int x = world.x() - origin.x(), y = world.y() - origin.y(), z = world.z() - origin.z();
        return BLOCKS.contains(x, y, z) ? BLOCKS.index(x, y, z) : -1;
    }
}

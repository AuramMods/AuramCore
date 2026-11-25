package art.arcane.auram.field;

import java.util.Arrays;
import java.util.List;

import static art.arcane.auram.field.VoxelGrid.VAPOR;

/** Reference airflow solver. Its vectors and topology are shared with the compute implementation. */
public final class WindField {
    public static final float MIN_WIND = 0.004f;

    public record Source(VoxelGrid.Position position, float x, float y, float z) {}

    private WindTopology topology;
    private float[] current = new float[WindTopology.NODES * 4];
    private float[] next = new float[current.length];
    private final float[] sourceValues = new float[current.length];
    private final int[] sourceStamps = new int[WindTopology.NODES];
    private int stamp;
    private boolean active;

    public boolean active() {
        return active;
    }

    public WindTopology topology() {
        return topology;
    }

    public float component(int node, int axis) {
        return node < 0 ? 0 : current[node * 4 + axis];
    }

    /** Samples the coarse field at a fine-cell center without interpolating through solid faces. */
    public void sample(int x, int y, int z, float[] result) {
        int center = topology.node(x, y, z);
        if (center < 0) {
            Arrays.fill(result, 0);
            return;
        }
        int adjacentX = topology.node(x + ((x & 1) == 0 ? -1 : 1), y, z);
        int adjacentY = topology.node(x, y + ((y & 1) == 0 ? -1 : 1), z);
        int adjacentZ = topology.node(x, y, z + ((z & 1) == 0 ? -1 : 1));
        for (int axis = 0; axis < 3; axis++) {
            float base = component(center, axis);
            float dx = adjacentX < 0 ? 0 : component(adjacentX, axis) - base;
            float dy = adjacentY < 0 ? 0 : component(adjacentY, axis) - base;
            float dz = adjacentZ < 0 ? 0 : component(adjacentZ, axis) - base;
            result[axis] = base + 0.25f * (dx + dy + dz);
        }
    }

    public void step(WindTopology updated, List<Source> continuous, List<Source> gusts) {
        if (updated != topology) move(updated);
        for (Source gust : gusts) addGust(gust);
        if (!active && continuous.isEmpty()) return;

        if (++stamp == 0) {
            Arrays.fill(sourceStamps, 0);
            stamp = 1;
        }
        for (Source source : continuous) {
            int node = topology.node(source.position);
            if (node < 0) continue;
            int i = node * 4;
            if (sourceStamps[node] != stamp) {
                sourceStamps[node] = stamp;
                sourceValues[i] = sourceValues[i + 1] = sourceValues[i + 2] = 0;
            }
            sourceValues[i] += source.x;
            sourceValues[i + 1] += source.y;
            sourceValues[i + 2] += source.z;
        }

        active = false;
        for (int node = 0; node < WindTopology.NODES; node++) {
            int i = node * 4;
            int descriptor = topology.descriptor(node);
            if (descriptor == 0) {
                write(next, i, 0, 0, 0);
                continue;
            }
            if (sourceStamps[node] == stamp) {
                active |= write(next, i, sourceValues[i], sourceValues[i + 1], sourceValues[i + 2]);
                continue;
            }

            float x = 0, y = 0, z = 0, weights = 0;
            for (int links = descriptor & WindTopology.LINK_MASK; links != 0; links &= links - 1) {
                int link = Integer.numberOfTrailingZeros(links);
                int face = link / WindTopology.COMPONENTS;
                int j = WindTopology.adjacent(node, face, link % WindTopology.COMPONENTS) * 4;
                float speed = current[j + 3];
                if (speed <= MIN_WIND) continue;
                int incoming = face ^ 1;
                int dx = WindTopology.DX[incoming], dy = WindTopology.DY[incoming], dz = WindTopology.DZ[incoming];
                float alignment = (current[j] * dx + current[j + 1] * dy + current[j + 2] * dz) / speed;
                float distanceLoss = (speed - 0.012f) * (0.78f + (alignment >= 0 ? 0.19f : 0.66f) * alignment);
                if (distanceLoss <= MIN_WIND) continue;

                // Flow turns at a bend, with more of its previous direction retained on straight runs.
                float turn = 0.72f * (1 - Math.max(0, alignment));
                float k = (1 - turn) / speed;
                float tx = current[j] * k + dx * turn;
                float ty = current[j + 1] * k + dy * turn;
                float tz = current[j + 2] * k + dz * turn;
                float weight = distanceLoss * distanceLoss;
                weight *= weight;
                float length = length(tx, ty, tz);
                if (length < MIN_WIND) continue;
                float scale = distanceLoss * weight / length;
                x += tx * scale;
                y += ty * scale;
                z += tz * scale;
                weights += weight;
            }
            if (weights > 0) {
                x /= weights;
                y /= weights;
                z /= weights;
            }
            float response = 0.27f + 0.7f * Math.max(current[i + 3], length(x, y, z));
            active |= write(next, i, lerp(current[i], x, response), lerp(current[i + 1], y, response),
                    lerp(current[i + 2], z, response));
        }

        float[] old = current;
        current = next;
        next = old;
    }

    private void addGust(Source source) {
        int node = topology.node(source.position);
        if (node < 0) return;
        int i = node * 4;
        active |= write(current, i, current[i] + source.x, current[i + 1] + source.y, current[i + 2] + source.z);
    }

    private void move(WindTopology updated) {
        if (topology != null && active) {
            var now = updated.origin();
            var old = topology.origin();
            Arrays.fill(next, 0);
            byte[] count = new byte[WindTopology.NODES];
            for (int z = 0; z < VAPOR.side(); z++) {
                for (int y = 0; y < VAPOR.side(); y++) {
                    for (int x = 0; x < VAPOR.side(); x++) {
                        int to = updated.node(x, y, z);
                        if (to < 0) continue;
                        int from = topology.node(x + now.x() - old.x(), y + now.y() - old.y(), z + now.z() - old.z());
                        if (from < 0) continue;
                        count[to]++;
                        for (int axis = 0; axis < 3; axis++) next[to * 4 + axis] += current[from * 4 + axis];
                    }
                }
            }
            active = false;
            for (int node = 0; node < WindTopology.NODES; node++) {
                if (count[node] == 0) continue;
                int i = node * 4;
                active |= write(next, i, next[i] / count[node], next[i + 1] / count[node], next[i + 2] / count[node]);
            }
            float[] oldValues = current;
            current = next;
            next = oldValues;
        }
        topology = updated;
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static float length(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static boolean write(float[] field, int i, float x, float y, float z) {
        float speed = length(x, y, z);
        float scale = speed < MIN_WIND ? 0 : speed > 1 ? 1 / speed : 1;
        field[i] = x * scale;
        field[i + 1] = y * scale;
        field[i + 2] = z * scale;
        field[i + 3] = speed * scale;
        return scale != 0;
    }
}

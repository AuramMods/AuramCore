package art.arcane.auram.field;

import java.nio.IntBuffer;
import java.util.Arrays;

import static art.arcane.auram.field.VoxelGrid.BLOCKS;

/** Connected air within each 2×2×2 cell and across its six faces. A cell has at most four components. */
public final class WindTopology {
    public static final VoxelGrid GRID = new VoxelGrid(32, 2);
    public static final int COMPONENTS = 4;
    public static final int NODES = GRID.cells() * COMPONENTS;
    public static final int LINK_MASK = 0x00ff_ffff;
    static final int[] DX = {1, -1, 0, 0, 0, 0};
    static final int[] DY = {0, 0, 1, -1, 0, 0};
    static final int[] DZ = {0, 0, 0, 0, 1, -1};
    private static final int[] NEIGHBOR = {1, -1, 32, -32, 1024, -1024};

    private final VoxelGrid.Position origin;
    private final int[] labels = new int[BLOCKS.cells()];
    /** Lower 24 bits are the connected component slots on the six neighbor faces. Upper byte is this component's air mask. */
    private final int[] nodes = new int[NODES];

    public WindTopology(VoxelGrid.Position origin, boolean[] open) {
        this.origin = origin;
        Arrays.fill(labels, -1);
        int side = GRID.side();
        for (int z = 0; z < side; z++) {
            for (int y = 0; y < side; y++) {
                for (int x = 0; x < side; x++) partition(x, y, z, open);
            }
        }

        int full = BLOCKS.side();
        for (int z = 0; z < full; z++) {
            for (int y = 0; y < full; y++) {
                for (int x = 0; x < full; x++) {
                    int node = labels[BLOCKS.index(x, y, z)];
                    if (node < 0) continue;
                    if ((x & 1) != 0 && x + 1 < full) link(node, labels[BLOCKS.index(x + 1, y, z)], 0);
                    if ((y & 1) != 0 && y + 1 < full) link(node, labels[BLOCKS.index(x, y + 1, z)], 2);
                    if ((z & 1) != 0 && z + 1 < full) link(node, labels[BLOCKS.index(x, y, z + 1)], 4);
                }
            }
        }
    }

    private void partition(int x, int y, int z, boolean[] open) {
        int remaining = 0;
        for (int corner = 0; corner < 8; corner++) {
            if (open[blockIndex(x, y, z, corner)]) remaining |= 1 << corner;
        }

        int node = GRID.index(x, y, z) * COMPONENTS;
        while (remaining != 0) {
            int adjacent = Integer.lowestOneBit(remaining);
            int component = 0;
            while (adjacent != 0) {
                component |= adjacent;
                adjacent = (((adjacent & 0x55) << 1) | ((adjacent & 0xaa) >>> 1)
                        | ((adjacent & 0x33) << 2) | ((adjacent & 0xcc) >>> 2)
                        | ((adjacent & 0x0f) << 4) | ((adjacent & 0xf0) >>> 4)) & remaining & ~component;
            }

            for (int bits = component; bits != 0; bits &= bits - 1) {
                labels[blockIndex(x, y, z, Integer.numberOfTrailingZeros(bits))] = node;
            }
            nodes[node++] = component << 24;
            remaining &= ~component;
        }
    }

    private void link(int from, int to, int face) {
        if (to < 0) return;
        nodes[from] |= 1 << (face * COMPONENTS + to % COMPONENTS);
        nodes[to] |= 1 << ((face ^ 1) * COMPONENTS + from % COMPONENTS);
    }

    public VoxelGrid.Position origin() {
        return origin;
    }

    public int node(int x, int y, int z) {
        return BLOCKS.contains(x, y, z) ? labels[BLOCKS.index(x, y, z)] : -1;
    }

    public int node(VoxelGrid.Position world) {
        return node(world.x() - origin.x(), world.y() - origin.y(), world.z() - origin.z());
    }

    public int descriptor(int node) {
        return nodes[node];
    }

    public void writeDescriptors(IntBuffer target) {
        target.put(nodes);
    }

    public void writeLabels(IntBuffer target) {
        target.put(labels);
    }

    public static int adjacent(int node, int face, int slot) {
        return (node / COMPONENTS + NEIGHBOR[face]) * COMPONENTS + slot;
    }

    public static int blockIndex(int x, int y, int z, int corner) {
        return BLOCKS.index(x * 2 + (corner & 1), y * 2 + ((corner >>> 1) & 1), z * 2 + (corner >>> 2));
    }
}

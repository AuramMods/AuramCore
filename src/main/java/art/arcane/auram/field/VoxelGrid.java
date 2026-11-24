package art.arcane.auram.field;

/** A cubic field grid. The window origin is in world blocks, while indices and bounds are in cells. */
public record VoxelGrid(int side, int blocksPerCell) {
    public static final int SECTION = 16;
    public static final int WINDOW_BLOCKS = 64;
    public static final VoxelGrid BLOCKS = new VoxelGrid(WINDOW_BLOCKS, 1);
    public static final VoxelGrid SECTIONS = new VoxelGrid(SECTION, 1);

    public int cells() {
        return side * side * side;
    }

    public int index(int x, int y, int z) {
        return x + side * (y + side * z);
    }

    public boolean contains(int x, int y, int z) {
        return x >= 0 && x < side && y >= 0 && y < side && z >= 0 && z < side;
    }

    public static Position originAround(int x, int y, int z) {
        return new Position(sectionOrigin(x) - WINDOW_BLOCKS / 2, sectionOrigin(y) - WINDOW_BLOCKS / 2,
                sectionOrigin(z) - WINDOW_BLOCKS / 2);
    }

    private static int sectionOrigin(int block) {
        return Math.floorDiv(block, SECTION) * SECTION;
    }

    public record Position(int x, int y, int z) {
        public Position section() {
            return new Position(Math.floorDiv(x, SECTION), Math.floorDiv(y, SECTION), Math.floorDiv(z, SECTION));
        }
    }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public static final Bounds EMPTY = new Bounds(0, 0, 0, 0, 0, 0);

        public boolean empty() {
            return minX == maxX;
        }

        public Bounds include(int x, int y, int z) {
            if (empty()) return new Bounds(x, y, z, x + 1, y + 1, z + 1);
            if (x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ) return this;
            return new Bounds(Math.min(minX, x), Math.min(minY, y), Math.min(minZ, z),
                    Math.max(maxX, x + 1), Math.max(maxY, y + 1), Math.max(maxZ, z + 1));
        }

        public Bounds union(Bounds other) {
            if (empty()) return other;
            if (other.empty()) return this;
            return new Bounds(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                    Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
        }

        public Bounds expand(int side) {
            return new Bounds(Math.max(0, minX - 1), Math.max(0, minY - 1), Math.max(0, minZ - 1),
                    Math.min(side, maxX + 1), Math.min(side, maxY + 1), Math.min(side, maxZ + 1));
        }
    }
}

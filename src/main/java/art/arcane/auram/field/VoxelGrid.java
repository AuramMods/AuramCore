package art.arcane.auram.field;

/** A cubic field grid. The window origin is in world blocks, while indices and bounds are in cells. */
public record VoxelGrid(int side, int blocksPerCell) {
    public static final int SECTION = 16;
    public static final int WINDOW_BLOCKS = 64;
    public static final VoxelGrid BLOCKS = new VoxelGrid(WINDOW_BLOCKS, 1);
    public static final VoxelGrid SECTIONS = new VoxelGrid(SECTION, 1);
    public static final int VAPOR_RADIUS = 128;
    public static final VoxelGrid WIND_BLOCKS = new VoxelGrid(68, 1);
    // Two guard blocks on each face cover the wind alignment and the texture filter.
    public static final VoxelGrid VAPOR = new VoxelGrid(2 * VAPOR_RADIUS + 4, 1);

    public int cells() {
        return side * side * side;
    }

    public int index(int x, int y, int z) {
        return x + side * (y + side * z);
    }

    public boolean contains(int x, int y, int z) {
        return x >= 0 && x < side && y >= 0 && y < side && z >= 0 && z < side;
    }

    public Position lastSection(Position origin) {
        int last = side * blocksPerCell - 1;
        return new Position(origin.x() + last, origin.y() + last, origin.z() + last).section();
    }

    public static Position vaporOriginAround(int x, int y, int z) {
        return new Position(vaporAxisOrigin(x), vaporAxisOrigin(y), vaporAxisOrigin(z));
    }

    private static int vaporAxisOrigin(int block) {
        // Snap to the nearest even coordinate so wind's 2-block cells stay aligned in the world.
        return ((block + 1) & ~1) - VAPOR.side() / 2;
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

        public int cells() {
            return (maxX - minX) * (maxY - minY) * (maxZ - minZ);
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
        }

        public int index(int x, int y, int z) {
            return x - minX + (maxX - minX) * (y - minY + (maxY - minY) * (z - minZ));
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

        public Bounds translateClipped(int x, int y, int z, int side) {
            if (empty() || (x == 0 && y == 0 && z == 0)) return this;
            int lowX = Math.max(0, minX + x), highX = Math.min(side, maxX + x);
            int lowY = Math.max(0, minY + y), highY = Math.min(side, maxY + y);
            int lowZ = Math.max(0, minZ + z), highZ = Math.min(side, maxZ + z);
            return lowX >= highX || lowY >= highY || lowZ >= highZ ? EMPTY
                    : new Bounds(lowX, lowY, lowZ, highX, highY, highZ);
        }
    }
}

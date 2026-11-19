package art.arcane.auram.map;

import net.minecraft.resources.ResourceLocation;

/** Coordinates refer to tiles. The same bounds determine request order and the server upload rate. */
public record SurfaceMapView(ResourceLocation dimension, int centerX, int centerZ, int minX, int minZ,
                             int maxX, int maxZ) {
    public static final int MAX_RADIUS = 96;
    private static final int WORLD_LIMIT = 30_000_000 / (16 * SurfaceTile.CHUNKS) + 1;

    public boolean valid() {
        return minX >= -WORLD_LIMIT && minZ >= -WORLD_LIMIT && maxX <= WORLD_LIMIT && maxZ <= WORLD_LIMIT
                && minX <= centerX && centerX <= maxX && minZ <= centerZ && centerZ <= maxZ
                && centerX - minX <= MAX_RADIUS && centerZ - minZ <= MAX_RADIUS
                && maxX - centerX <= MAX_RADIUS && maxZ - centerZ <= MAX_RADIUS;
    }

    public boolean contains(SurfaceTile.Key key) {
        return dimension.equals(key.dimension()) && key.x() >= minX && key.x() <= maxX
                && key.z() >= minZ && key.z() <= maxZ;
    }

    /** Both HUD maps are centered on the same player. Keep their visible extent under one rate limit. */
    public SurfaceMapView union(SurfaceMapView other) {
        return new SurfaceMapView(dimension, centerX, centerZ,
                Math.max(centerX - MAX_RADIUS, Math.min(minX, other.minX)),
                Math.max(centerZ - MAX_RADIUS, Math.min(minZ, other.minZ)),
                Math.min(centerX + MAX_RADIUS, Math.max(maxX, other.maxX)),
                Math.min(centerZ + MAX_RADIUS, Math.max(maxZ, other.maxZ)));
    }

    public long distanceSquared(SurfaceTile.Key key) {
        return distanceSquared(key, centerX, centerZ);
    }

    static long distanceSquared(SurfaceTile.Key key, int x, int z) {
        long dx = (long) key.x() - x;
        long dz = (long) key.z() - z;
        // Catalog entries and validated coordinates lie within the world border.
        return dx * dx + dz * dz;
    }

    static boolean isWorldTile(int x, int z) {
        return Math.abs((long) x) <= WORLD_LIMIT && Math.abs((long) z) <= WORLD_LIMIT;
    }
}

package art.arcane.auram.client;

public final class MinimapZoom {
    public static final double EXTRA_WIDE_ZOOM = 0.5;
    public static final int STOCK_TEXTURE_CHUNKS = 15;
    private static final double STOCK_WIDEST_ZOOM = 1.0;
    // Double the stock 7-chunk radius while keeping the player's chunk at the center.
    private static final int WIDE_TEXTURE_CHUNKS = 2 * (STOCK_TEXTURE_CHUNKS - 1) + 1;

    private MinimapZoom() {}

    public static double easeOut(double progress) {
        // Normalize the exponential so the last interpolated sample meets the exact target.
        return (1.0 - Math.pow(2.0, -10.0 * progress)) / (1.0 - 1.0 / 1024.0);
    }

    public static double keyTarget(double currentTarget, boolean zoomIn, double stockTarget) {
        if (zoomIn && currentTarget < STOCK_WIDEST_ZOOM) {
            return STOCK_WIDEST_ZOOM;
        }
        if (!zoomIn && currentTarget <= STOCK_WIDEST_ZOOM) {
            return EXTRA_WIDE_ZOOM;
        }
        return stockTarget;
    }

    public static int textureChunks(float displayedZoom, double targetZoom) {
        return displayedZoom < STOCK_WIDEST_ZOOM || targetZoom < STOCK_WIDEST_ZOOM
                ? WIDE_TEXTURE_CHUNKS : STOCK_TEXTURE_CHUNKS;
    }
}

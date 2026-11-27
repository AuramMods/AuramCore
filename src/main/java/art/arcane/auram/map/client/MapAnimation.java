package art.arcane.auram.map.client;

final class MapAnimation {
    static final long DURATION = 1_000_000_000L;
    private static final double EXPONENT = 10 * Math.log(2);
    private static final double END = -Math.expm1(-EXPONENT);

    private MapAnimation() {}

    static double easeOut(long elapsed) {
        if (elapsed <= 0) return 0;
        if (elapsed >= DURATION) return 1;
        return -Math.expm1(-EXPONENT * elapsed / DURATION) / END;
    }
}

package art.arcane.auram.rf;

public final class RfPower {
    private static final long PEAK_RF_PER_TICK = 100_000_000L;

    private RfPower() {}

    public static double intensity(long rate) {
        if (rate <= 0) return 0.0;
        if (rate >= PEAK_RF_PER_TICK) return 1.0;
        return Math.log10(1.0 + rate) / 8.0;
    }

    static float vaporRate(long rate) {
        double power = intensity(rate);
        return (float) (power * (0.05 + 0.10 * power));
    }
}

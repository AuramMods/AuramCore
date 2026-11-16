package art.arcane.auram.smoke;

/** Test-only, thread-confined counters; disabled outside the measured call. */
public final class ComparisonProbe {
    private static final ThreadLocal<long[]> COUNTS = new ThreadLocal<>();
    public static void start() { COUNTS.set(new long[2]); }
    public static long[] stop() { long[] result = COUNTS.get(); COUNTS.remove(); return result; }
    public static void record(Object first, Object second) {
        long[] counts = COUNTS.get();
        if (counts != null) { counts[0]++; if (first == second) counts[1]++; }
    }
}

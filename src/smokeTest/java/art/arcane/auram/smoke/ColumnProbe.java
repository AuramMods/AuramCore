package art.arcane.auram.smoke;

public final class ColumnProbe {
    public static final ThreadLocal<long[]> CALLS = ThreadLocal.withInitial(() -> new long[]{-1});
    public static final ThreadLocal<long[]> NOISE = ThreadLocal.withInitial(() -> new long[]{-1});
    private static final ThreadLocal<long[]> LAST_NOISE = ThreadLocal.withInitial(() -> new long[]{0});
    public static void start() { CALLS.get()[0] = 0; NOISE.get()[0] = 0; }
    public static long stop() {
        long count=CALLS.get()[0];CALLS.get()[0]=-1;
        LAST_NOISE.get()[0]=NOISE.get()[0];NOISE.get()[0]=-1;
        return count;
    }
    public static long lastNoise() { return LAST_NOISE.get()[0]; }
}

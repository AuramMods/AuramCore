package art.arcane.auram.smoke;

public final class ColumnProbe {
    public static final ThreadLocal<long[]> CALLS = ThreadLocal.withInitial(() -> new long[]{-1});
    public static void start() { CALLS.get()[0] = 0; }
    public static long stop() { long count = CALLS.get()[0]; CALLS.get()[0] = -1; return count; }
}

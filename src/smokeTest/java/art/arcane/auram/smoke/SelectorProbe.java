package art.arcane.auram.smoke;

public final class SelectorProbe {
    private static final ThreadLocal<long[]> ACTIVE = new ThreadLocal<>();
    public static void start() { ACTIVE.set(new long[2]); }
    public static long stop() { long value=ACTIVE.get()[0]; ACTIVE.remove(); return value; }
    public static long value() { long[] value=ACTIVE.get();return value==null?0:value[0]; }
    public static void record() { long[] value=ACTIVE.get(); if(value!=null) value[0]++; }
    public static long globalScans() { long[] value=ACTIVE.get(); return value==null ? 0 : value[1]; }
    public static void recordGlobalScan() { long[] value=ACTIVE.get(); if(value!=null) value[1]++; }
}

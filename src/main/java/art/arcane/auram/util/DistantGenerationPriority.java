package art.arcane.auram.util;

/** Reserve generation capacity for nearby player chunks while one distant group can keep progressing. */
public final class DistantGenerationPriority {
    private DistantGenerationPriority() { }

    public static boolean shouldDefer(boolean nearbyChunksPending, int runningGroups) {
        return nearbyChunksPending && runningGroups > 0;
    }
}

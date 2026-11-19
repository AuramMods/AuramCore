package art.arcane.auram.util;

import java.util.concurrent.TimeUnit;

/** Give visible chunks a head start while still admitting distant work under sustained demand. */
public final class DistantGenerationPriority {
    private static final long FOREGROUND_WINDOW = TimeUnit.SECONDS.toNanos(1);
    private boolean yielding;
    private long windowStart;

    public boolean shouldDefer(boolean foregroundPending, int runningGroups, long now) {
        if (!foregroundPending) {
            yielding = false;
            return false;
        }
        if (!yielding) {
            windowStart = now;
            yielding = true;
        }
        if (runningGroups != 0 || now - windowStart < FOREGROUND_WINDOW) return true;

        // At most one new group gets through this window. Already-running work can finish.
        windowStart = now;
        return false;
    }
}

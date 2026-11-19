package art.arcane.auram.map;

import java.util.concurrent.TimeUnit;

/** Server-side pacing against a monotonic clock, independent of the user's TPS. */
final class TileUploadRate {
    private long due;
    private boolean started;

    boolean ready(long now) {
        return !started || now - due >= 0;
    }

    void sent(long now, double perSecond) {
        long interval = Math.round(TimeUnit.SECONDS.toNanos(1) / perSecond);
        // Preserve the schedule across normal tick jitter, reset after a longer idle gap.
        due = (started && now - due < interval ? due : now) + interval;
        started = true;
    }
}

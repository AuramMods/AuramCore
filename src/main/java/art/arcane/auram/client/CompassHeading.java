package art.arcane.auram.client;

public final class CompassHeading {
    private static final long SETTLE_NANOS = 1_000_000_000L;
    private static final double EXP_END = Math.exp(-6.0);
    private boolean initialized;
    private long startedAt;
    private float input;
    private float start;
    private float target;

    public float update(long now, float yaw) {
        if (!initialized) {
            initialized = true;
            input = start = target = yaw;
            startedAt = now;
            return yaw;
        }
        if (wrap(yaw - input) != 0) {
            start = sample(now);
            target = start + wrap(yaw - start);
            input = yaw;
            startedAt = now;
            return start;
        }
        return sample(now);
    }

    private float sample(long now) {
        long elapsed = now - startedAt;
        if (elapsed >= SETTLE_NANOS) return input;
        double progress = (1.0 - Math.exp(-6.0 * elapsed / SETTLE_NANOS)) / (1.0 - EXP_END);
        return start + (target - start) * (float) progress;
    }

    public void reset() {
        initialized = false;
    }

    private static float wrap(float angle) {
        float wrapped = angle % 360;
        if (wrapped >= 180) return wrapped - 360;
        if (wrapped < -180) return wrapped + 360;
        return wrapped;
    }
}

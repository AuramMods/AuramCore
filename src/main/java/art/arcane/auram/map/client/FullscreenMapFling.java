package art.arcane.auram.map.client;

public final class FullscreenMapFling {
    private static final long STALE_NANOS = 100_000_000L;
    private static final double DECAY = 7.0;
    private static final double MAX_SPEED = 2_400;
    private static final double MIN_RELEASE_SPEED = 70;
    private static final double STOP_SPEED = 5;

    private long updatedAt;
    private double velocityX;
    private double velocityY;
    private boolean gliding;

    public void begin(long now) {
        stop();
        updatedAt = now;
    }

    public void drag(long now, double dx, double dy) {
        double seconds = Math.max(0.001, Math.min(0.05, (now - updatedAt) * 1e-9));
        updatedAt = now;
        double speed = Math.hypot(dx, dy) / seconds;
        double scale = speed > MAX_SPEED ? MAX_SPEED / speed : 1;
        double weight = -Math.expm1(-seconds / 0.025);
        velocityX += (dx * scale / seconds - velocityX) * weight;
        velocityY += (dy * scale / seconds - velocityY) * weight;
    }

    public void release(long now) {
        long idle = now - updatedAt;
        double retained = Math.max(0, 1 - (double) idle / STALE_NANOS);
        velocityX *= retained;
        velocityY *= retained;
        updatedAt = now;
        gliding = Math.hypot(velocityX, velocityY) >= MIN_RELEASE_SPEED;
    }

    public Offset advance(long now) {
        if (!gliding) return Offset.NONE;

        double speed = Math.hypot(velocityX, velocityY);
        double seconds = (now - updatedAt) * 1e-9;
        double timeToStop = Math.log(speed / STOP_SPEED) / DECAY;
        // Integrate exponential decay over the frame so distance is independent of frame rate.
        double elapsed = Math.min(seconds, timeToStop);
        double distanceFactor = -Math.expm1(-DECAY * elapsed) / DECAY;
        var offset = new Offset(velocityX * distanceFactor, velocityY * distanceFactor);
        if (seconds >= timeToStop) {
            stop();
        } else {
            double retained = Math.exp(-DECAY * elapsed);
            velocityX *= retained;
            velocityY *= retained;
            updatedAt = now;
        }
        return offset;
    }

    public void stop() {
        velocityX = 0;
        velocityY = 0;
        gliding = false;
    }

    public record Offset(double x, double y) {
        private static final Offset NONE = new Offset(0, 0);
    }
}

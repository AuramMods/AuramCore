package art.arcane.auram.field;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** A fixed-cadence client worker. Slow steps skip missed ticks instead of queuing catch-up work. */
public final class FieldTicker implements AutoCloseable {
    public static final long TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private final ScheduledExecutorService scheduler;
    private final Runnable update;
    private boolean closed;
    private long nextTick = System.nanoTime();

    public FieldTicker(String name, Runnable update) {
        this.update = update;
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        scheduler.execute(this::run);
    }

    private void run() {
        try {
            update.run();
        } finally {
            scheduleNext();
        }
    }

    private synchronized void scheduleNext() {
        if (closed) return;
        long now = System.nanoTime();
        nextTick += TICK_NANOS;
        if (nextTick <= now) nextTick = now + TICK_NANOS;
        scheduler.schedule(this::run, nextTick - now, TimeUnit.NANOSECONDS);
    }

    @Override
    public synchronized void close() {
        closed = true;
        scheduler.shutdownNow();
    }
}

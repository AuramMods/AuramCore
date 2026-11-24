package art.arcane.auram.vapor.client;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class VaporWorker implements AutoCloseable {
    static final long TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private static final int MAX_PENDING_PULSES = 32;
    private static final Logger LOGGER = LogUtils.getLogger();

    record Frames(VaporVolume.Snapshot previous, VaporVolume.Snapshot current, long publishedAt) {
        static final Frames EMPTY = new Frames(null, null, 0);
    }

    private final VaporVolume volume = new VaporVolume();
    private final ArrayDeque<VaporVolume.Source> pulses = new ArrayDeque<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Auram Vapor");
        thread.setDaemon(true);
        return thread;
    });
    private VaporVolume.Occupancy input;
    private VaporVolume.Erosion erosion = VaporVolume.Erosion.CLEAR;
    private List<VaporVolume.Source> emitters = List.of();
    private boolean paused;
    private volatile boolean closed;
    private volatile Frames frames = Frames.EMPTY;
    private long nextTick = System.nanoTime();

    VaporWorker() {
        scheduler.execute(this::run);
    }

    synchronized void submit(VaporVolume.Occupancy occupancy, VaporVolume.Erosion erosion,
                             List<VaporVolume.Source> sources, boolean paused, List<VaporVolume.Source> additions) {
        if (closed) return;
        this.input = occupancy;
        this.erosion = erosion;
        this.emitters = sources;
        this.paused = paused;
        for (VaporVolume.Source pulse : additions) {
            if (pulses.size() == MAX_PENDING_PULSES) break;
            pulses.addLast(pulse);
        }
    }

    Frames frames() {
        return closed ? Frames.EMPTY : frames;
    }

    private void run() {
        try {
            tick();
        } catch (RuntimeException failure) {
            LOGGER.error("Vapor simulation stopped after an unexpected error", failure);
            close();
        } finally {
            scheduleNext();
        }
    }

    private void tick() {
        VaporVolume.Occupancy occupancy;
        VaporVolume.Erosion atmosphere;
        List<VaporVolume.Source> pending;
        List<VaporVolume.Source> continuous;
        synchronized (this) {
            if (closed || paused || input == null) return;
            occupancy = input;
            atmosphere = erosion;
            pending = List.copyOf(pulses);
            continuous = emitters;
            pulses.clear();
        }
        VaporVolume.Snapshot snapshot = volume.step(occupancy, atmosphere, pending, continuous);
        synchronized (this) {
            if (!closed) {
                VaporVolume.Snapshot previous = frames.current;
                if (previous != null && !previous.origin.equals(occupancy.origin())) previous = snapshot;
                frames = previous == null && snapshot == null ? Frames.EMPTY : new Frames(previous, snapshot, System.nanoTime());
            }
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
        pulses.clear();
        emitters = List.of();
        input = null;
        frames = Frames.EMPTY;
        scheduler.shutdownNow();
    }
}

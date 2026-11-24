package art.arcane.auram.vapor.client;

import art.arcane.auram.field.FieldFrames;
import art.arcane.auram.field.FieldTicker;
import art.arcane.auram.field.SourceInbox;
import art.arcane.auram.field.VoxelOccupancy;
import art.arcane.auram.field.WindField;
import art.arcane.auram.field.WindTopology;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.List;

final class VaporWorker implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final VaporVolume volume = new VaporVolume();
    private final SourceInbox<VaporVolume.Source> sources = new SourceInbox<>(32);
    private final SourceInbox<WindField.Source> windSources = new SourceInbox<>(32);
    private WindField wind;
    private VoxelOccupancy windOccupancy;
    private final FieldTicker ticker = new FieldTicker("Auram Vapor", this::run);
    private VoxelOccupancy input;
    private VaporVolume.Erosion erosion = VaporVolume.Erosion.CLEAR;
    private boolean paused;
    private volatile boolean closed;
    private volatile FieldFrames<VaporVolume.Snapshot> frames = FieldFrames.empty();

    VaporWorker() {
        ticker.start();
    }

    synchronized void submit(VoxelOccupancy occupancy, VaporVolume.Erosion erosion,
                             List<VaporVolume.Source> continuous, boolean paused, List<VaporVolume.Source> additions,
                             List<WindField.Source> windEmitters, List<WindField.Source> gusts) {
        if (closed) return;
        this.input = occupancy;
        this.erosion = erosion;
        this.paused = paused;
        sources.submit(continuous, additions);
        windSources.submit(windEmitters, gusts);
    }

    FieldFrames<VaporVolume.Snapshot> frames() {
        return closed ? FieldFrames.empty() : frames;
    }

    private void run() {
        try {
            tick();
        } catch (RuntimeException failure) {
            LOGGER.error("Voxel field simulation stopped after an unexpected error", failure);
            close();
        }
    }

    private void tick() {
        VoxelOccupancy occupancy;
        VaporVolume.Erosion atmosphere;
        List<VaporVolume.Source> pending;
        List<VaporVolume.Source> continuous;
        List<WindField.Source> windEmitters;
        List<WindField.Source> gusts;
        synchronized (this) {
            if (closed || paused || input == null) return;
            occupancy = input;
            atmosphere = erosion;
            pending = sources.takePulses();
            continuous = sources.continuous();
            windEmitters = windSources.continuous();
            gusts = windSources.takePulses();
        }
        if (!windEmitters.isEmpty() || !gusts.isEmpty() || wind != null && wind.active()) {
            if (wind == null) wind = new WindField();
            if (occupancy != windOccupancy) {
                boolean[] open = volume.updateOccupancy(occupancy);
                wind.step(new WindTopology(occupancy.origin(), open), windEmitters, gusts);
                windOccupancy = occupancy;
            } else {
                wind.step(wind.topology(), windEmitters, gusts);
            }
        }
        VaporVolume.Snapshot snapshot = volume.step(occupancy, atmosphere, pending, continuous, wind, null);
        synchronized (this) {
            if (!closed) {
                VaporVolume.Snapshot current = frames.current();
                frames = frames.advance(snapshot, current == null || current.origin.equals(occupancy.origin()));
            }
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        sources.clear();
        windSources.clear();
        input = null;
        frames = FieldFrames.empty();
        ticker.close();
    }
}

package art.arcane.auram.vapor.client;

import art.arcane.auram.field.FieldFrames;
import art.arcane.auram.field.FieldTicker;
import art.arcane.auram.field.SourceInbox;
import art.arcane.auram.field.VoxelOccupancy;
import art.arcane.auram.field.WindField;
import art.arcane.auram.field.WindTopology;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.List;

final class VaporWorker implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final FieldProbe probe = FieldProbe.create("cpu");
    private VaporVolume volume;
    private final ArrayDeque<VaporCompute.Inputs> computeInputs = new ArrayDeque<>(2);
    private boolean compute;
    private VoxelOccupancy computeOccupancy;
    private boolean[] computeOpen;
    private WindTopology computeTopology;
    private boolean computeHasWind;
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
        compute = VaporCompute.available();
        LOGGER.info("Voxel field backend: {}", compute ? "OpenGL compute" : "CPU");
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

    synchronized boolean usesCompute() {
        return !closed && compute;
    }

    synchronized VaporCompute.Inputs takeCompute() {
        return closed || paused ? null : computeInputs.pollFirst();
    }

    synchronized void fallBack(VaporCompute.Inputs failed) {
        compute = false;
        restoreSources(failed);
        for (var pending : computeInputs) restoreSources(pending);
        computeInputs.clear();
    }

    private void restoreSources(VaporCompute.Inputs input) {
        sources.submit(sources.continuous(), input.pulses());
        windSources.submit(windSources.continuous(), input.gusts());
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
        boolean gpu;
        synchronized (this) {
            if (closed || paused || input == null) return;
            gpu = compute;
            if (gpu && computeInputs.size() >= 2) return;
            occupancy = input;
            atmosphere = erosion;
            pending = sources.takePulses();
            continuous = sources.continuous();
            windEmitters = windSources.continuous();
            gusts = windSources.takePulses();
        }
        if (gpu) {
            prepareCompute(occupancy, atmosphere, pending, continuous, gusts, windEmitters);
            return;
        }
        long start = probe == null ? 0 : System.nanoTime();
        if (volume == null) volume = new VaporVolume();
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
        if (probe != null) probe.positionOutlet(occupancy.origin());
        VaporVolume.Snapshot snapshot = volume.step(occupancy, atmosphere, pending, continuous, wind, probe);
        if (probe != null && probe.measure(start)) probe.log(wind);
        synchronized (this) {
            if (!closed) {
                VaporVolume.Snapshot current = frames.current();
                frames = frames.advance(snapshot, current == null || current.origin.equals(occupancy.origin()));
            }
        }
    }

    private void prepareCompute(VoxelOccupancy occupancy, VaporVolume.Erosion erosion,
                                List<VaporVolume.Source> pulses, List<VaporVolume.Source> emitters,
                                List<WindField.Source> gusts, List<WindField.Source> windEmitters) {
        computeHasWind |= !gusts.isEmpty() || !windEmitters.isEmpty();
        if (occupancy != computeOccupancy) {
            computeOpen = occupancy.openBlocks();
            computeTopology = null;
            computeOccupancy = occupancy;
        }
        if (computeHasWind && computeTopology == null) {
            computeTopology = new WindTopology(occupancy.origin(), computeOpen);
        }
        var commands = new VaporCompute.Inputs(occupancy, computeOpen, computeTopology, erosion,
                pulses, emitters, gusts, windEmitters);
        synchronized (this) {
            if (closed) return;
            if (compute) computeInputs.addLast(commands);
            else restoreSources(commands);
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        sources.clear();
        windSources.clear();
        computeInputs.clear();
        input = null;
        frames = FieldFrames.empty();
        ticker.close();
    }
}

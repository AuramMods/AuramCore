package art.arcane.auram.rf.client;

import art.arcane.auram.rf.RfFlow;
import art.arcane.auram.rf.RfPower;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Visual history only. The server's measured RF and network protocol remain unchanged. */
final class RfPresentation {
    private static final int MAX_TRACKS = 1024;
    private static final int MAX_CODE = 248;
    private static final double RELEASE_SECONDS = 2.0;
    private static final double SPACING = 2.0;

    private final Map<RfFlow.Edge, Track> tracks = new LinkedHashMap<>();
    private List<RfFlow.Sample> input = List.of();
    private List<Clock> clocks = List.of();
    private long previousNanos;

    Collection<Track> tracks() {
        return tracks.values();
    }

    List<Clock> clocks() {
        return clocks;
    }

    void reset(long now) {
        tracks.clear();
        input = List.of();
        clocks = List.of();
        previousNanos = now;
    }

    /** Returns true when the texture's spatial data changed. Clocks advance every frame. */
    boolean step(List<RfFlow.Sample> samples, long now) {
        double dt = Math.max(0.0, (now - previousNanos) / 1_000_000_000.0);
        previousNanos = now;
        boolean topologyChanged = false;
        if (samples != input) {
            tracks.values().forEach(track -> {
                track.forward.target = 0;
                track.backward.target = 0;
            });
            for (var sample : samples) {
                Track track = tracks.get(sample.edge());
                if (track == null) {
                    track = new Track(sample.edge());
                    tracks.put(sample.edge(), track);
                    topologyChanged = true;
                }
                track.forward.target = encodeRate(sample.forward());
                track.backward.target = encodeRate(sample.backward());
            }
            input = samples;
        }

        boolean ratesChanged = false;
        var iterator = tracks.values().iterator();
        while (iterator.hasNext()) {
            Track track = iterator.next();
            ratesChanged |= track.forward.update(dt) | track.backward.update(dt);
            if (track.forward.idle() && track.backward.idle()
                    || tracks.size() > MAX_TRACKS && track.forward.target == 0 && track.backward.target == 0) {
                iterator.remove();
                topologyChanged = true;
            }
        }
        if (topologyChanged) rebuildClocks();
        for (Clock clock : clocks) clock.advance(dt);
        return topologyChanged || ratesChanged;
    }

    private void rebuildClocks() {
        var atBlock = new HashMap<Long, List<Track>>();
        for (Track track : tracks.values()) {
            atBlock.computeIfAbsent(track.edge.position(), ignored -> new ArrayList<>()).add(track);
            atBlock.computeIfAbsent(track.end, ignored -> new ArrayList<>()).add(track);
        }
        var visited = new HashSet<Track>();
        var next = new ArrayList<Clock>();
        for (Track root : tracks.values()) {
            if (!visited.add(root)) continue;
            var connected = new ArrayList<Track>();
            connected.add(root);
            Clock previous = null;
            for (int i = 0; i < connected.size(); i++) {
                Track track = connected.get(i);
                if (previous == null && track.clock != null) previous = track.clock;
                addNeighbors(atBlock.remove(track.edge.position()), visited, connected);
                addNeighbors(atBlock.remove(track.end), visited, connected);
            }
            var clock = new Clock(next.size(), connected, previous);
            for (Track track : connected) track.clock = clock;
            next.add(clock);
        }
        clocks = next;
    }

    private static void addNeighbors(List<Track> neighbors, HashSet<Track> visited, List<Track> connected) {
        if (neighbors == null) return;
        for (Track neighbor : neighbors) {
            if (visited.add(neighbor)) connected.add(neighbor);
        }
    }

    private static int encodeRate(long amount) {
        return (int) Math.round(RfPower.intensity(amount) * 248.0);
    }

    private static double approach(double value, double target, double dt, double timeConstant) {
        return target + (value - target) * Math.exp(-dt / timeConstant);
    }

    static final class Track {
        final RfFlow.Edge edge;
        final long end;
        private final EasedRate forward = new EasedRate();
        private final EasedRate backward = new EasedRate();
        Clock clock;

        Track(RfFlow.Edge edge) {
            this.edge = edge;
            int axis = edge.axis();
            this.end = BlockPos.offset(edge.position(), axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0);
        }

        int packedRates() {
            return forward.code | (backward.code << 8);
        }
    }

    static final class Clock {
        final int slot;
        private final List<Track> tracks;
        private final double seed;
        private double time;
        private double phase;
        private double speed;

        Clock(int slot, List<Track> tracks, Clock previous) {
            this.slot = slot;
            this.tracks = tracks;
            seed = previous == null
                    ? (Long.hashCode(tracks.get(0).edge.position()) & 0xffff) / 65536.0 * (2.0 * Math.PI)
                    : previous.seed;
            time = previous == null ? 0.0 : previous.time;
            phase = previous == null ? seed / Math.PI : previous.phase;
            speed = previous == null ? 1.5 : previous.speed;
        }

        void advance(double dt) {
            double peak = 0.0;
            for (Track track : tracks) {
                peak = Math.max(peak, Math.max(track.forward.value, track.backward.value));
            }
            double power = peak / MAX_CODE;
            double targetSpeed = 1.5 + 7.0 * power * power * (3.0 - 2.0 * power);
            speed = approach(speed, targetSpeed, dt, 0.23);
            time += dt;
            double drift = 1.0 + 0.19 * Math.sin(time * 3.7 + seed) + 0.12 * Math.sin(time * 7.9 - seed);
            phase = (phase + dt * speed * drift) % SPACING;
        }

        int encodedPhase() {
            return Float.floatToRawIntBits((float) phase);
        }
    }

    private static final class EasedRate {
        int target;
        int code;
        double value;
        double secondsUnpowered;

        boolean update(double dt) {
            secondsUnpowered = target == 0 ? secondsUnpowered + dt : 0.0;
            double constant = target > value ? 0.19 : (target == 0 ? 1.32 : 0.32);
            value = secondsUnpowered >= RELEASE_SECONDS ? 0.0 : approach(value, target, dt, constant);
            int next = (int) Math.round(value);
            boolean changed = code != next;
            code = next;
            return changed;
        }

        boolean idle() {
            return target == 0 && code == 0;
        }
    }
}

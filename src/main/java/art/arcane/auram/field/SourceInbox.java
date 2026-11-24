package art.arcane.auram.field;

import java.util.ArrayDeque;
import java.util.List;

/** The latest continuous sources and a bounded queue of one-shot sources. The worker owns the lock. */
public final class SourceInbox<T> {
    private final int pulseLimit;
    private final ArrayDeque<T> pulses = new ArrayDeque<>();
    private List<T> continuous = List.of();

    public SourceInbox(int pulseLimit) {
        this.pulseLimit = pulseLimit;
    }

    public void submit(List<T> continuous, List<T> additions) {
        this.continuous = continuous;
        for (T pulse : additions) {
            if (pulses.size() >= pulseLimit) break;
            pulses.addLast(pulse);
        }
    }

    public List<T> continuous() {
        return continuous;
    }

    public List<T> takePulses() {
        List<T> result = List.copyOf(pulses);
        pulses.clear();
        return result;
    }

    public void clear() {
        pulses.clear();
        continuous = List.of();
    }
}

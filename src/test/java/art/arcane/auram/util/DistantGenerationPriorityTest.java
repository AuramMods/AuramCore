package art.arcane.auram.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DistantGenerationPriorityTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    public void waitsForForegroundAndResumesAtOnceWhenReady() {
        var priority = new DistantGenerationPriority();
        assertFalse(priority.shouldDefer(false, 2, 0));
        assertTrue(priority.shouldDefer(true, 0, 1));
        assertFalse(priority.shouldDefer(false, 0, 2));
        assertTrue(priority.shouldDefer(true, 0, 2 * SECOND));
    }

    @Test
    public void admitsOneGroupPerWindowUnderSustainedDemand() {
        var priority = new DistantGenerationPriority();
        assertTrue(priority.shouldDefer(true, 0, 0));
        assertTrue(priority.shouldDefer(true, 0, SECOND - 1));
        assertTrue(priority.shouldDefer(true, 1, SECOND));
        assertFalse(priority.shouldDefer(true, 0, SECOND + 1));
        assertTrue(priority.shouldDefer(true, 0, SECOND + 2));
        assertFalse(priority.shouldDefer(true, 0, 2 * SECOND + 1));
    }
}

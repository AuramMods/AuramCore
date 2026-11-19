package art.arcane.auram.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DistantGenerationPriorityTest {
    @Test
    public void allowsOneGroupImmediatelyWhileNearbyChunksArePending() {
        assertFalse(DistantGenerationPriority.shouldDefer(true, 0));
        assertTrue(DistantGenerationPriority.shouldDefer(true, 1));
        assertTrue(DistantGenerationPriority.shouldDefer(true, 3));
    }

    @Test
    public void leavesAdmissionToDistantHorizonsOnceNearbyChunksAreReady() {
        assertFalse(DistantGenerationPriority.shouldDefer(false, 0));
        assertFalse(DistantGenerationPriority.shouldDefer(false, 3));
    }
}

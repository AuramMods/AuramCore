package art.arcane.auram.map.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FullscreenMapFlingTest {
    @Test
    public void aFlickKeepsItsDirectionAndSlowsDown() {
        var fling = flick();
        var first = fling.advance(120_000_000);
        var next = fling.advance(170_000_000);
        assertTrue(first.x() > next.x() && next.x() > 0);
        assertEquals(-first.x() / 2, first.y(), 1e-9);
        assertEquals(-next.x() / 2, next.y(), 1e-9);

        fling.advance(2_000_000_000);
        assertEquals(0, fling.advance(3_000_000_000L).x(), 0);
    }

    @Test
    public void waitingBeforeReleaseOrStartingAnotherDragCancelsMomentum() {
        var fling = new FullscreenMapFling();
        fling.begin(0);
        fling.drag(30_000_000, 40, 0);
        fling.release(150_000_000);
        assertEquals(0, fling.advance(200_000_000).x(), 0);

        fling = flick();
        fling.begin(80_000_000);
        fling.release(90_000_000);
        assertEquals(0, fling.advance(100_000_000).x(), 0);
    }

    @Test
    public void frameRateDoesNotChangeGlideDistance() {
        var singleFrame = flick();
        var multipleFrames = flick();
        var whole = singleFrame.advance(2_000_000_000);
        double summed = 0;
        for (long now = 80_000_000; now <= 2_000_000_000; now += 10_000_000) {
            summed += multipleFrames.advance(now).x();
        }
        assertEquals(whole.x(), summed, 1e-9);
    }

    private static FullscreenMapFling flick() {
        var fling = new FullscreenMapFling();
        fling.begin(0);
        fling.drag(30_000_000, 30, -15);
        fling.drag(60_000_000, 30, -15);
        fling.release(70_000_000);
        return fling;
    }
}

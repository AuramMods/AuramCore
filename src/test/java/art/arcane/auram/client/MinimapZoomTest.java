package art.arcane.auram.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MinimapZoomTest {
    @Test
    public void easingStartsFastAndMeetsBothEndpoints() {
        assertEquals(0.0, MinimapZoom.easeOut(0), 0.0);
        assertEquals(1.0, MinimapZoom.easeOut(1), 0.0);
        assertTrue(MinimapZoom.easeOut(0.25) > 0.8);
        assertTrue(MinimapZoom.easeOut(0.75) > MinimapZoom.easeOut(0.5));
    }

    @Test
    public void extraStepConnectsToTheStockZoomRange() {
        assertEquals(0.5, MinimapZoom.keyTarget(1.0, false, 1.0), 0.0);
        assertEquals(0.5, MinimapZoom.keyTarget(0.5, false, 1.0), 0.0);
        assertEquals(1.0, MinimapZoom.keyTarget(0.5, true, 1.5), 0.0);
        assertEquals(1.0, MinimapZoom.keyTarget(2.0, false, 1.0), 0.0);
        assertEquals(2.0, MinimapZoom.keyTarget(1.0, true, 2.0), 0.0);
    }

    @Test
    public void wideTextureCoversBothDirectionsOfTheTransition() {
        assertEquals(17, MinimapZoom.textureChunks(1.0F, 2.0));
        assertEquals(31, MinimapZoom.textureChunks(1.0F, 0.5));
        int wideChunks = MinimapZoom.textureChunks(0.75F, 1.0);
        assertEquals(31, wideChunks);
        // At the player's chunk edge, the widest view still has a full guard chunk.
        assertTrue((wideChunks / 2) * 16 >= 112.0 / MinimapZoom.EXTRA_WIDE_ZOOM + 16.0);
    }
}

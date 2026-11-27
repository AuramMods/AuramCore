package art.arcane.auram.map.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FullscreenMapAnimationTest {
    @Test
    public void keepsTheWorldAtTheCursorThroughFractionalZoomAndRegionChanges() {
        var zoom = new FullscreenMapZoom(20);
        double anchor = zoom.regionAt(-8, -23.25, 375);
        zoom.retarget(10, 375, 65, 100);
        assertEquals(20, zoom.size(), 0);
        assertTrue(zoom.advance(80_000_100));
        assertTrue(zoom.size() < 20 && zoom.size() > 10);
        assertTrue(zoom.scale() >= 1);
        double offset = zoom.scrollFor(-13, anchor, 375);
        assertEquals(anchor, zoom.regionAt(-13, offset, 375), 1e-12);

        zoom.advance(1_000_000_100);
        offset = zoom.scrollFor(-13, anchor, 375);
        assertEquals(10, zoom.size(), 0);
        assertEquals(anchor, zoom.regionAt(-13, offset, 375), 1e-12);
    }

    @Test
    public void reversesFromTheDisplayedZoomWithAnUpdatedCursor() {
        var zoom = new FullscreenMapZoom(2);
        zoom.retarget(4, 10, 20, 0);
        zoom.advance(125_000_000);
        double before = zoom.size();
        zoom.retarget(2, 340, 175, 125_000_000);
        assertFalse(zoom.advance(125_000_000));
        assertEquals(before, zoom.size(), 0);
        assertEquals(340, zoom.cursorX(), 0);
        assertEquals(175, zoom.cursorY(), 0);
        zoom.advance(625_000_000);
        assertTrue(zoom.size() - 2 < (before - 2) / 8);
        zoom.advance(1_125_000_000);
        assertEquals(2, zoom.size(), 0);
    }

    @Test
    public void onlyFirstUploadsFadeAndPreviousUploadsStayOpaque() {
        var fades = new MapTileFades();
        int[] alphas = new int[MapTileFades.CHUNK_COUNT];
        fades.uploaded(index -> index == 0, false, 0);
        fades.uploaded(index -> index <= 1, true, 25);
        assertTrue(fades.fillAlphas(alphas, 25));
        assertEquals(255, alphas[0]);
        assertEquals(0, alphas[1]);

        fades.uploaded(index -> index <= 2, true, 250_000_025);
        assertTrue(fades.fillAlphas(alphas, 250_000_025));
        assertTrue(alphas[1] > 190 && alphas[1] < 255);
        assertEquals(0, alphas[2]);

        assertFalse(fades.fillAlphas(alphas, 1_250_000_025));
        fades.releaseImage();
        fades.uploaded(index -> index <= 2, true, 2_000_000_025);
        assertFalse(fades.fillAlphas(alphas, 2_000_000_025));
    }
}

package art.arcane.auram.client.menu;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MapMenuViewTest {
    @Test
    public void usesSixteenChunksWhenCovered() {
        int zoom = MapMenuView.widestCoveredZoom(960, ignored -> true);
        assertEquals(128.0, MapMenuView.halfSpan(960, zoom), 0.0);
        assertEquals(72.0, MapMenuView.halfSpan(540, zoom), 0.0);
    }

    @Test
    public void stopsAtTheFirstZoomWithCompleteCoverage() {
        int zoom = MapMenuView.widestCoveredZoom(960, candidate -> candidate >= 2017);
        assertEquals(2017, zoom);
    }

    @Test
    public void leavesNoViewWhenEvenTheTightestFrameIsMissingTiles() {
        assertEquals(0, MapMenuView.widestCoveredZoom(960, ignored -> false));
    }
}

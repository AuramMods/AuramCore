package art.arcane.auram.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CompassHeadingTest {
    @Test
    public void deceleratesAndSettlesExactlyAfterHalfASecond() {
        CompassHeading heading = new CompassHeading();
        assertEquals(0, heading.update(0, 0), 0.0F);
        assertEquals(0, heading.update(10, 90), 0.0F);
        float first = heading.update(50_000_010, 90);
        float next = heading.update(100_000_010, 90);
        assertTrue(first > 0 && next > first && next - first < first);
        assertTrue(heading.update(499_000_010, 90) < 90);
        assertEquals(90, heading.update(500_000_010, 90), 0.0F);
    }

    @Test
    public void crossesNorthByTheShortestPathAndKeepsRetargetsContinuous() {
        CompassHeading heading = new CompassHeading();
        assertEquals(359, heading.update(0, 359), 0.0F);
        assertEquals(359, heading.update(1, 1), 0.0F);
        float crossing = heading.update(150_000_001, 1);
        assertTrue(crossing > 360 && crossing < 361);
        assertEquals(crossing, heading.update(150_000_001, 20), 0.0F);
        assertEquals(20, heading.update(650_000_001, 20), 0.0F);
    }
}

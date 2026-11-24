package art.arcane.auram.rf;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RfPowerTest {
    @Test
    public void vaporFollowsTheSameLogScaleAsTheRfEffect() {
        assertEquals(0.0f, RfPower.vaporRate(0), 0.0f);
        assertEquals(0.5, RfPower.intensity(9_999), 1e-12);
        assertEquals(0.05f, RfPower.vaporRate(9_999), 1e-6f);
        assertEquals(0.15f, RfPower.vaporRate(100_000_000), 1e-6f);
        assertEquals(0.15f, RfPower.vaporRate(Long.MAX_VALUE), 1e-6f);
        assertTrue(RfPower.vaporRate(100) < RfPower.vaporRate(10_000));
        assertTrue(RfPower.vaporRate(10_000) < RfPower.vaporRate(1_000_000));
    }
}

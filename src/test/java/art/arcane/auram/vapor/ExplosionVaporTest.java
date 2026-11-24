package art.arcane.auram.vapor;

import net.minecraft.core.BlockPos;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ExplosionVaporTest {
    @Test
    public void scalesDensityAndSpreadAroundTheTntBaseline() {
        assertEquals(new VaporNetwork.Pulse(BlockPos.ZERO, 0.25f, 2), VaporDemonstrators.explosionPulse(BlockPos.ZERO, 1.0f));
        assertEquals(new VaporNetwork.Pulse(BlockPos.ZERO, 0.75f, 5), VaporDemonstrators.explosionPulse(BlockPos.ZERO, 3.0f));
        assertEquals(new VaporNetwork.Pulse(BlockPos.ZERO, 1.0f, 7), VaporDemonstrators.explosionPulse(BlockPos.ZERO, 4.0f));
        assertEquals(new VaporNetwork.Pulse(BlockPos.ZERO, 1.0f, 11), VaporDemonstrators.explosionPulse(BlockPos.ZERO, 6.0f));
        assertEquals(new VaporNetwork.Pulse(BlockPos.ZERO, 1.0f, Vapor.MAX_RADIUS),
                VaporDemonstrators.explosionPulse(BlockPos.ZERO, 100.0f));
    }

    @Test
    public void ignoresBlastsWithoutAPositiveFiniteRadius() {
        assertNull(VaporDemonstrators.explosionPulse(BlockPos.ZERO, 0.0f));
        assertNull(VaporDemonstrators.explosionPulse(BlockPos.ZERO, -1.0f));
        assertNull(VaporDemonstrators.explosionPulse(BlockPos.ZERO, Float.NaN));
        assertNull(VaporDemonstrators.explosionPulse(BlockPos.ZERO, Float.POSITIVE_INFINITY));
    }
}

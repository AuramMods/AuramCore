package art.arcane.auram.light;

import net.minecraft.core.BlockPos;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LightSampleTest {
    @Test
    public void pulseFadesBetweenTicksAndExpires() {
        LightSample light = new LightSample(BlockPos.ZERO, 0x72B9FF, 0.8f, 120, 20);
        assertEquals(0xCC72B9FF, light.packedColor(100, 0f));
        assertEquals(0x6672B9FF, light.packedColor(110, 0f));
        assertTrue((light.packedColor(110, 0.5f) >>> 24) < 0x66);
        assertFalse(light.expired(119));
        assertTrue(light.expired(120));
        assertEquals(0, light.packedColor(120, 0f));
    }

    @Test
    public void steadyEmitterKeepsItsIntensity() {
        LightSample light = new LightSample(BlockPos.ZERO, 0xFF8833, 0.4f, 0, 0);
        assertFalse(light.expired(12000));
        assertEquals(0x66FF8833, light.packedColor(12000, 0.6f));
    }
}

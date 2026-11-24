package art.arcane.auram.vapor.client;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VaporAtmosphereTest {
    @Test
    public void ambientFogFollowsWeatherHeightAndDimension() {
        var fogOptions = Map.of("RainFog_amount", "0.07", "NETHER_HAZE_DENSITY", "0.77");
        VaporAtmosphere.updateBliss(fogOptions::get, unused -> false, null);
        try {
            var fog = VaporAtmosphere.current();
            var clear = fog.sample("minecraft:overworld", 6000, 0.0f);
            assertTrue(clear.clear());
            var rain = fog.sample("minecraft:overworld", 6000, 1.0f);
            assertEquals(0.5f, fog.lingering(rain, 0, 176, 0, true), 1e-6f);
            assertEquals(0.0f, fog.lingering(rain, 0, 176, 0, false), 0.0f);

            var morning = fog.sample("minecraft:overworld", 0, 0.0f);
            assertTrue(fog.lingering(morning, 0, 63, 0, false) > fog.lingering(morning, 0, 96, 0, false));
            var nether = fog.sample("minecraft:the_nether", 6000, 0.0f);
            assertTrue(fog.lingering(nether, 0, 63, 0, false) > 0.0f);
            assertTrue(fog.lingering(nether, 0, 96, 0, false) > fog.lingering(nether, 0, 63, 0, false));
        } finally {
            VaporAtmosphere.clearBliss();
        }
    }
}

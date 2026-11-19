package art.arcane.auram.map;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class TileUploadRateTest {
    @Test
    public void defaultRatesAndImmediateFirstUpload() {
        assertEquals(100, uploadsInTenSeconds(10));
        assertEquals(10, uploadsInTenSeconds(1));
        assertEquals(5, uploadsInTenSeconds(0.5));
    }

    @Test
    public void jitterPreservesSustainedRateButIdleDoesNotAccumulateCredits() {
        TileUploadRate rate = new TileUploadRate();
        long start = TimeUnit.SECONDS.toNanos(1);
        rate.sent(start, 10);
        assertFalse(rate.ready(start + ms(99)));
        assertTrue(rate.ready(start + ms(126)));
        rate.sent(start + ms(126), 10);
        assertTrue(rate.ready(start + ms(200)));
        rate.sent(start + ms(5000), 10);
        assertFalse(rate.ready(start + ms(5001)));
    }

    private static int uploadsInTenSeconds(double tilesPerSecond) {
        TileUploadRate rate = new TileUploadRate();
        int uploads = 0;
        for (long elapsed = 0; elapsed < 10_000; elapsed += 50) {
            if (rate.ready(ms(elapsed))) {
                rate.sent(ms(elapsed), tilesPerSecond);
                uploads++;
            }
        }
        return uploads;
    }

    private static long ms(long value) {
        return TimeUnit.MILLISECONDS.toNanos(value);
    }
}

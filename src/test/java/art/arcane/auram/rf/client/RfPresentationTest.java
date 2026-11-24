package art.arcane.auram.rf.client;

import art.arcane.auram.rf.RfFlow;
import net.minecraft.core.BlockPos;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class RfPresentationTest {
    @Test
    public void poweringAndUnpoweringEasesTheVisualRate() {
        var presentation = new RfPresentation();
        var sample = sample(0, 0, 0, 100_000_000L);
        presentation.reset(0);
        presentation.step(List.of(sample), 0);
        var track = presentation.tracks().iterator().next();
        presentation.step(List.of(sample), 200_000_000);
        int rising = track.packedRates();
        assertTrue(rising > 0 && rising < 248);

        presentation.step(List.of(sample), 1_000_000_000);
        int running = track.packedRates();
        assertTrue(running > rising && running <= 248);

        presentation.step(List.of(), 2_000_000_000);
        assertTrue(track.packedRates() > 0 && track.packedRates() < running);
        presentation.step(List.of(), 3_000_000_000L);
        assertTrue(presentation.tracks().isEmpty());
    }

    @Test
    public void turnsShareAClockAndPowerChangesKeepItsPhase() {
        var presentation = new RfPresentation();
        presentation.reset(0);
        var first = sample(0, 0, 0, 50_000L);
        var turn = sample(1, 0, 2, 50_000L);
        var separate = sample(10, 0, 0, 100_000_000L);
        presentation.step(List.of(first, turn, separate), 500_000_000);

        var iterator = presentation.tracks().iterator();
        var firstTrack = iterator.next();
        var turnTrack = iterator.next();
        var separateTrack = iterator.next();
        assertSame(firstTrack.clock, turnTrack.clock);
        assertNotSame(firstTrack.clock, separateTrack.clock);
        var clock = firstTrack.clock;
        int phase = clock.encodedPhase();

        presentation.step(List.of(sample(0, 0, 0, 20_000_000L), turn, separate), 510_000_000);
        assertSame(clock, firstTrack.clock);
        assertEquals(firstTrack.clock.encodedPhase(), turnTrack.clock.encodedPhase());
        assertTrue(phase != clock.encodedPhase());
    }

    private static RfFlow.Sample sample(int x, int z, int axis, long rate) {
        return new RfFlow.Sample(new RfFlow.Edge(new BlockPos(x, 75, z).asLong(), axis), rate, 0);
    }
}

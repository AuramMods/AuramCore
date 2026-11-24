package art.arcane.auram.vapor.client;

import org.junit.Test;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

import static art.arcane.auram.vapor.client.VaporVolume.*;
import static org.junit.Assert.*;

public class VaporVolumeTest {
    private static final Position ORIGIN = new Position(0, 0, 0);
    private static final Position SOURCE = new Position(7, 5, 7);
    private static final Source PULSE = new Source(SOURCE, 1.0f, 0);
    private static final Source EMITTER = new Source(SOURCE, 0.11f, 0);

    @Test
    public void pulseRisesAndDissipatesAfterTheSourceStops() {
        VaporVolume volume = new VaporVolume();
        Occupancy air = occupancy(new BitSet());
        Snapshot first = volume.step(air, List.of(PULSE), List.of());
        assertTrue(density(first, 7, 5, 7) > 0);
        assertTrue(density(first, 7, 5, 7) < 64);
        Snapshot snapshot = volume.step(air, List.of(), List.of());
        assertTrue(density(snapshot, 7, 6, 7) > density(snapshot, 7, 4, 7));
        assertTrue(density(snapshot, 8, 5, 7) > 0);
        for (int i = 0; i < 400; i++) snapshot = volume.step(air, List.of(), List.of());
        assertNull(snapshot);
        assertTrue(density(first, 7, 5, 7) > 0);
    }

    @Test
    public void roofRedirectsAContinuousStreamAndUnloadingClearsIt() {
        BitSet blocked = new BitSet();
        for (int z = 0; z < SECTION; z++) {
            for (int x = 0; x < SECTION; x++) blocked.set(sectionIndex(x, 6, z));
        }
        Occupancy roof = occupancy(blocked);
        VaporVolume volume = new VaporVolume();
        Snapshot snapshot = null;
        for (int i = 0; i < 40; i++) snapshot = volume.step(roof, List.of(), List.of(EMITTER));
        assertTrue(density(snapshot, 7, 5, 7) > density(snapshot, 8, 5, 7));
        assertTrue(density(snapshot, 8, 5, 7) > 0);
        assertFalse(snapshot.open(index(7, 6, 7)));
        assertEquals(0, density(snapshot, 7, 7, 7));
        assertNull(volume.step(new Occupancy(ORIGIN, Map.of()), List.of(), List.of()));
    }

    @Test
    public void sealedCellApproachesFullOpacityThenErodesEvenWithoutAnOutlet() {
        BitSet sealed = new BitSet();
        sealed.set(0, SECTION * SECTION * SECTION);
        sealed.clear(sectionIndex(7, 5, 7));
        Occupancy wall = occupancy(sealed);
        VaporVolume volume = new VaporVolume();
        Snapshot snapshot = volume.step(wall, List.of(PULSE), List.of());
        assertTrue(density(snapshot, 7, 5, 7) < 40);
        for (int i = 1; i < 20; i++) snapshot = volume.step(wall, List.of(), List.of(EMITTER));
        assertTrue(density(snapshot, 7, 5, 7) > 225);
        for (int i = 0; i < 500; i++) snapshot = volume.step(wall, List.of(), List.of());
        assertNull(snapshot);
    }

    @Test
    public void areaPulseRespectsWallsAndFallsOffWithDistance() {
        BitSet blocked = new BitSet();
        for (int z = 0; z < SECTION; z++) {
            for (int y = 0; y < SECTION; y++) blocked.set(sectionIndex(8, y, z));
        }
        VaporVolume volume = new VaporVolume();
        Snapshot snapshot = volume.step(occupancy(blocked), List.of(new Source(SOURCE, 1.0f, 5)), List.of());
        assertTrue(density(snapshot, 7, 5, 7) > density(snapshot, 5, 5, 7));
        assertTrue(density(snapshot, 5, 5, 7) > density(snapshot, 2, 5, 7));
        assertEquals(0, density(snapshot, 9, 5, 7));
    }

    @Test
    public void movingWindowKeepsWorldPositionAndNewWallsRemoveTrappedDensity() {
        Position section = new Position(1, 0, 0);
        Position emitter = new Position(21, 5, 7);
        SectionOccupancy air = new SectionOccupancy(new BitSet());
        VaporVolume volume = new VaporVolume();
        Snapshot before = volume.step(new Occupancy(ORIGIN, Map.of(section, air)),
                List.of(new Source(emitter, 1.0f, 0)), List.of());
        assertTrue(density(before, 21, 5, 7) > 0);
        Position shifted = new Position(16, 0, 0);
        Snapshot after = volume.step(new Occupancy(shifted, Map.of(section, air)), List.of(), List.of());
        assertTrue(density(after, 5, 5, 7) > 0);
        SectionOccupancy newWall = air.with(5, 5, 7, true);
        Snapshot blocked = volume.step(new Occupancy(shifted, Map.of(section, newWall)), List.of(), List.of());
        assertEquals(0, density(blocked, 5, 5, 7));
        assertTrue(before.open(index(21, 5, 7)));
        assertEquals(new Position(-48, -32, -32), Position.originAround(-1, 0, 0));
    }

    private static Occupancy occupancy(BitSet blocked) {
        return new Occupancy(ORIGIN, Map.of(ORIGIN, new SectionOccupancy(blocked)));
    }

    private static int density(Snapshot snapshot, int x, int y, int z) {
        return snapshot.density(index(x, y, z));
    }
}

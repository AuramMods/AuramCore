package art.arcane.auram.vapor.client;

import art.arcane.auram.field.VoxelGrid.Position;
import art.arcane.auram.field.VoxelOccupancy;
import art.arcane.auram.field.VoxelOccupancy.SectionOccupancy;
import org.junit.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import static art.arcane.auram.field.VoxelGrid.*;
import static art.arcane.auram.vapor.client.VaporVolume.*;
import static org.junit.Assert.*;

public class VaporVolumeTest {
    private static final Position ORIGIN = new Position(0, 0, 0);
    private static final Position SOURCE = new Position(7, 5, 7);
    private static final Source PULSE = new Source(SOURCE, 1.0f, 0);
    private static final Source EMITTER = new Source(SOURCE, 0.11f, 0);
    private static final Erosion NO_EROSION = Erosion.sample((x, y, z) -> 1.0f);

    @Test
    public void pulseRisesAndDissipatesAfterTheSourceStops() {
        VaporVolume volume = new VaporVolume();
        VoxelOccupancy air = occupancy(new BitSet());
        Snapshot first = volume.step(air, Erosion.CLEAR, List.of(PULSE), List.of());
        assertTrue(density(first, 7, 5, 7) > 0);
        assertTrue(density(first, 7, 5, 7) < 64);
        Snapshot snapshot = volume.step(air, Erosion.CLEAR, List.of(), List.of());
        assertTrue(density(snapshot, 7, 6, 7) > density(snapshot, 7, 4, 7));
        assertTrue(density(snapshot, 8, 5, 7) > 0);
        for (int i = 0; i < 400; i++) snapshot = volume.step(air, Erosion.CLEAR, List.of(), List.of());
        assertNull(snapshot);
        assertTrue(density(first, 7, 5, 7) > 0);
    }

    @Test
    public void roofRedirectsAContinuousStreamAndUnloadingClearsIt() {
        BitSet blocked = new BitSet();
        for (int z = 0; z < SECTION; z++) {
            for (int x = 0; x < SECTION; x++) blocked.set(SECTIONS.index(x, 6, z));
        }
        VoxelOccupancy roof = occupancy(blocked);
        VaporVolume volume = new VaporVolume();
        Snapshot snapshot = null;
        for (int i = 0; i < 40; i++) snapshot = volume.step(roof, Erosion.CLEAR, List.of(), List.of(EMITTER));
        assertTrue(density(snapshot, 7, 5, 7) > density(snapshot, 8, 5, 7));
        assertTrue(density(snapshot, 8, 5, 7) > 0);
        assertFalse(snapshot.open(index(7, 6, 7)));
        assertEquals(0, density(snapshot, 7, 7, 7));
        assertNull(volume.step(new VoxelOccupancy(ORIGIN, Map.of()), Erosion.CLEAR, List.of(), List.of()));
    }

    @Test
    public void transferAcceptsOnlyTheDestinationsFreeCapacity() {
        float accepted = acceptedTransfer(0.3f, 0.8f);
        assertEquals(0.2f, accepted, 1e-6f);
        assertEquals(0.1f, 0.3f - accepted, 1e-6f);
        assertEquals(0.0f, acceptedTransfer(0.3f, 1.0f), 0.0f);
        assertEquals(0.3f, acceptedTransfer(0.3f, 0.4f), 0.0f);
    }

    @Test
    public void excessRisingVaporStaysInTheLowerCell() {
        BitSet sealed = new BitSet();
        sealed.set(0, SECTION * SECTION * SECTION);
        sealed.clear(SECTIONS.index(7, 5, 7));
        sealed.clear(SECTIONS.index(7, 6, 7));
        VoxelOccupancy room = occupancy(sealed);
        VaporVolume volume = new VaporVolume();
        volume.step(room, NO_EROSION, List.of(
                new Source(SOURCE, 0.3f, 0), new Source(new Position(7, 6, 7), 0.98f, 0)), List.of());

        Snapshot snapshot = volume.step(room, NO_EROSION, List.of(), List.of());
        // The upper cell holds 1.0, leaving 0.28 below after both transfers.
        assertEquals(18, density(snapshot, 7, 5, 7));
        assertEquals(61, density(snapshot, 7, 6, 7));
    }

    @Test
    public void aFullRoomStaysFullUntilErosionMakesSpace() {
        BitSet sealed = new BitSet();
        sealed.set(0, SECTION * SECTION * SECTION);
        VaporVolume volume = new VaporVolume();
        var initial = new ArrayList<Source>();
        for (int y = 4; y <= 7; y++) {
            sealed.clear(SECTIONS.index(7, y, 7));
            initial.add(new Source(new Position(7, y, 7), 1.0f, 0));
        }
        VoxelOccupancy room = occupancy(sealed);
        Snapshot snapshot = volume.step(room, NO_EROSION, initial, List.of());
        for (int i = 0; i < 60; i++) snapshot = volume.step(room, NO_EROSION, List.of(), List.of());
        for (int y = 4; y <= 7; y++) assertEquals(255, density(snapshot, 7, y, 7));

        for (int i = 0; i < 400; i++) snapshot = volume.step(room, Erosion.CLEAR, List.of(), List.of());
        assertNull(snapshot);
    }

    @Test
    public void aSteadyEmitterFillsTheCeilingBeforeTheFloor() {
        BitSet sealed = new BitSet();
        sealed.set(0, SECTION * SECTION * SECTION);
        for (int y = 4; y <= 10; y++) {
            for (int z = 7; z <= 8; z++) {
                for (int x = 7; x <= 8; x++) sealed.clear(SECTIONS.index(x, y, z));
            }
        }
        VoxelOccupancy room = occupancy(sealed);
        VaporVolume volume = new VaporVolume();
        var source = new Source(new Position(7, 4, 7), 0.32f, 0);
        Snapshot snapshot = null;
        for (int i = 0; i < 40; i++) snapshot = volume.step(room, NO_EROSION, List.of(), List.of(source));
        assertTrue(density(snapshot, 8, 10, 8) > 220);
        assertTrue(density(snapshot, 8, 4, 8) < 80);

        for (int i = 0; i < 120; i++) snapshot = volume.step(room, NO_EROSION, List.of(), List.of(source));
        assertEquals(255, density(snapshot, 8, 10, 8));
        assertTrue(density(snapshot, 8, 4, 8) > 150);
    }

    @Test
    public void sealedCellApproachesFullOpacityThenErodesEvenWithoutAnOutlet() {
        BitSet sealed = new BitSet();
        sealed.set(0, SECTION * SECTION * SECTION);
        sealed.clear(SECTIONS.index(7, 5, 7));
        VoxelOccupancy wall = occupancy(sealed);
        VaporVolume volume = new VaporVolume();
        Snapshot snapshot = volume.step(wall, Erosion.CLEAR, List.of(PULSE), List.of());
        assertTrue(density(snapshot, 7, 5, 7) < 40);
        for (int i = 1; i < 20; i++) snapshot = volume.step(wall, Erosion.CLEAR, List.of(), List.of(EMITTER));
        assertTrue(density(snapshot, 7, 5, 7) > 225);
        for (int i = 0; i < 500; i++) snapshot = volume.step(wall, Erosion.CLEAR, List.of(), List.of());
        assertNull(snapshot);
    }

    @Test
    public void lingeringControlsErosionTimeAndClampsAtNoErosion() {
        float clear = Erosion.retentionFor(0.0f);
        float rain = Erosion.retentionFor(0.5f);
        assertEquals(clear, rain * rain, 1e-7f);
        assertEquals(clear, Erosion.retentionFor(-1.0f), 0.0f);
        assertEquals(1.0f, Erosion.retentionFor(1.0f), 0.0f);
        assertEquals(1.0f, Erosion.retentionFor(2.0f), 0.0f);
    }

    @Test
    public void vaporOutlastsClearWeatherInAnEnclosedFoggyCell() {
        BitSet sealed = new BitSet();
        sealed.set(0, SECTION * SECTION * SECTION);
        sealed.clear(SECTIONS.index(7, 5, 7));
        sealed.clear(SECTIONS.index(10, 5, 7));
        VoxelOccupancy room = occupancy(sealed);
        Erosion fog = Erosion.sample((x, y, z) -> x >= 8 ? 0.5f : 0.0f);
        VaporVolume volume = new VaporVolume();
        Source foggy = new Source(new Position(10, 5, 7), 1.0f, 0);
        Snapshot snapshot = volume.step(room, fog, List.of(PULSE, foggy), List.of());
        for (int i = 0; i < 80; i++) snapshot = volume.step(room, fog, List.of(), List.of());
        assertTrue(density(snapshot, 10, 5, 7) > density(snapshot, 7, 5, 7) * 2);

        Erosion saturated = Erosion.sample((x, y, z) -> 1.0f);
        for (int i = 0; i < 400; i++) snapshot = volume.step(room, saturated, List.of(), List.of());
        int held = density(snapshot, 10, 5, 7);
        assertTrue(held > 0);
        for (int i = 0; i < 400; i++) snapshot = volume.step(room, saturated, List.of(), List.of());
        assertEquals(held, density(snapshot, 10, 5, 7));

        for (int i = 0; i < 400; i++) snapshot = volume.step(room, Erosion.CLEAR, List.of(), List.of());
        assertNull(snapshot);
    }

    @Test
    public void areaPulseRespectsWallsAndFallsOffWithDistance() {
        BitSet blocked = new BitSet();
        for (int z = 0; z < SECTION; z++) {
            for (int y = 0; y < SECTION; y++) blocked.set(SECTIONS.index(8, y, z));
        }
        VaporVolume volume = new VaporVolume();
        Snapshot snapshot = volume.step(occupancy(blocked), Erosion.CLEAR, List.of(new Source(SOURCE, 1.0f, 5)), List.of());
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
        Snapshot before = volume.step(new VoxelOccupancy(ORIGIN, Map.of(section, air)), Erosion.CLEAR,
                List.of(new Source(emitter, 1.0f, 0)), List.of());
        assertTrue(density(before, 21, 5, 7) > 0);
        Position shifted = new Position(16, 0, 0);
        Snapshot after = volume.step(new VoxelOccupancy(shifted, Map.of(section, air)), Erosion.CLEAR, List.of(), List.of());
        assertTrue(density(after, 5, 5, 7) > 0);
        SectionOccupancy newWall = air.with(5, 5, 7, true);
        Snapshot blocked = volume.step(new VoxelOccupancy(shifted, Map.of(section, newWall)), Erosion.CLEAR, List.of(), List.of());
        assertEquals(0, density(blocked, 5, 5, 7));
        assertTrue(before.open(index(21, 5, 7)));
        assertEquals(new Position(-48, -32, -32), originAround(-1, 0, 0));
    }

    private static VoxelOccupancy occupancy(BitSet blocked) {
        return new VoxelOccupancy(ORIGIN, Map.of(ORIGIN, new SectionOccupancy(blocked)));
    }

    private static int density(Snapshot snapshot, int x, int y, int z) {
        return snapshot.density(index(x, y, z));
    }
}

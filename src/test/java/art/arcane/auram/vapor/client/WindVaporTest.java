package art.arcane.auram.vapor.client;

import art.arcane.auram.field.VoxelGrid.Position;
import art.arcane.auram.field.VoxelOccupancy;
import art.arcane.auram.field.VoxelOccupancy.SectionOccupancy;
import art.arcane.auram.field.WindField;
import art.arcane.auram.field.WindTopology;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import static art.arcane.auram.field.VoxelGrid.SECTIONS;
import static org.junit.Assert.*;

public class WindVaporTest {
    private static final Position ORIGIN = new Position(0, 0, 0);
    private static final VaporVolume.Source SOURCE = new VaporVolume.Source(new Position(7, 6, 7), .22f, 0);
    private static final WindField.Source FAN = new WindField.Source(new Position(5, 6, 7), .3f, 0, 0);

    @Test
    public void poweredSideVentApproachesTheOpenCeilingFlux() {
        double off = fixture(false, false);
        double on = fixture(false, true);
        double ceiling = fixture(true, false);
        System.out.printf("net Vapor/tick: side off %.5f, side 0.30 %.5f, ceiling %.5f, ratio %.4f%n",
                off, on, ceiling, on / ceiling);
        assertTrue("The ceiling reference must actually vent", ceiling > 0.05);
        assertTrue("The blocked ceiling must reduce ventilation", off < ceiling * 0.2);
        assertTrue("The 0.30 fan must approach the ceiling throughput", on >= ceiling * 0.8);
    }

    @Test
    public void diagonalWindDrivesBothAxesAndOpposingWindRestoresBaseline() throws Exception {
        var terrain = new VoxelOccupancy(ORIGIN, Map.of(ORIGIN, new SectionOccupancy(new BitSet())));
        var topology = new WindTopology(ORIGIN, terrain.openBlocks());
        var wind = new WindField();
        Position center = new Position(6, 6, 6);
        WindField.Source diagonal = new WindField.Source(center, .55f, 0, .55f);
        wind.step(topology, List.of(diagonal), List.of());
        VaporVolume.Source vapor = new VaporVolume.Source(center, .8f, 0);
        var fullRetention = VaporVolume.Erosion.sample((x, y, z) -> 1);
        var volume = new VaporVolume();
        volume.step(terrain, fullRetention, List.of(vapor), List.of());
        volume.step(terrain, fullRetention, List.of(), List.of(), wind, null);
        float east = density(volume, 7, 6, 6);
        float south = density(volume, 6, 6, 7);
        assertEquals(east, south, 1e-6f);
        assertTrue(east > .3f);
        assertTrue(density(volume, 6, 7, 6) < .03f);

        var cancelled = new WindField();
        cancelled.step(topology, List.of(diagonal, new WindField.Source(center, -.55f, 0, -.55f)), List.of());
        var left = new VaporVolume();
        var right = new VaporVolume();
        left.step(terrain, fullRetention, List.of(vapor), List.of());
        right.step(terrain, fullRetention, List.of(vapor), List.of());
        var baseline = left.step(terrain, fullRetention, List.of(), List.of());
        var actual = right.step(terrain, fullRetention, List.of(), List.of(), cancelled, null);
        assertEquals(baseline.bounds, actual.bounds);
        for (int i = 0; i < VaporVolume.CELLS; i++) assertEquals(baseline.density(i), actual.density(i));
    }

    @Test
    public void blockedOrSaturatedDestinationsKeepRejectedVaporInItsSource() throws Exception {
        BitSet blocked = new BitSet(SECTIONS.cells());
        blocked.set(0, SECTIONS.cells());
        blocked.clear(SECTIONS.index(6, 6, 6));
        blocked.clear(SECTIONS.index(7, 6, 6));
        var terrain = new VoxelOccupancy(ORIGIN, Map.of(ORIGIN, new SectionOccupancy(blocked)));
        var topology = new WindTopology(ORIGIN, terrain.openBlocks());
        var wind = new WindField();
        Position from = new Position(6, 6, 6);
        Position to = new Position(7, 6, 6);
        wind.step(topology, List.of(new WindField.Source(from, .3f, 0, 0)), List.of());
        var volume = new VaporVolume();
        var fullRetention = VaporVolume.Erosion.sample((x, y, z) -> 1);
        volume.step(terrain, fullRetention, List.of(new VaporVolume.Source(from, .3f, 0),
                new VaporVolume.Source(to, .98f, 0)), List.of());
        for (int tick = 0; tick < 30; tick++) {
            volume.step(terrain, fullRetention, List.of(), List.of(), wind, null);
            assertEquals(1.28f, density(volume, 6, 6, 6) + density(volume, 7, 6, 6), 1e-5f);
            assertTrue(density(volume, 7, 6, 6) > .99f);
            assertTrue(density(volume, 6, 6, 6) >= .28f - 1e-5f);
        }
    }

    private static float density(VaporVolume volume, int x, int y, int z) throws Exception {
        Field field = VaporVolume.class.getDeclaredField("density");
        field.setAccessible(true);
        return ((float[]) field.get(volume))[VaporVolume.index(x, y, z)];
    }

    private static double fixture(boolean ceiling, boolean fan) {
        BitSet blocked = new BitSet(SECTIONS.cells());
        blocked.set(0, SECTIONS.cells());
        for (int z = 5; z <= 9; z++) {
            for (int y = 4; y <= 8; y++) {
                for (int x = 5; x <= 9; x++) blocked.clear(SECTIONS.index(x, y, z));
            }
        }
        if (ceiling) {
            blocked.clear(SECTIONS.index(7, 9, 7));
            for (int z = 3; z < 13; z++) for (int y = 10; y < 16; y++) for (int x = 3; x < 13; x++)
                blocked.clear(SECTIONS.index(x, y, z));
        } else {
            blocked.clear(SECTIONS.index(10, 6, 7));
            for (int z = 3; z < 13; z++) for (int y = 2; y < 12; y++) for (int x = 11; x < 16; x++)
                blocked.clear(SECTIONS.index(x, y, z));
        }

        int inside = ceiling ? VaporVolume.index(7, 8, 7) : VaporVolume.index(9, 6, 7);
        int outside = ceiling ? VaporVolume.index(7, 9, 7) : VaporVolume.index(10, 6, 7);
        var terrain = new VoxelOccupancy(ORIGIN, Map.of(ORIGIN, new SectionOccupancy(blocked)));
        var volume = new VaporVolume();
        var topology = new WindTopology(ORIGIN, terrain.openBlocks());
        var wind = new WindField();
        double[] cross = new double[1];
        VaporVolume.FlowMeter meter = (from, to, amount) -> {
            if (from == inside && to == outside) cross[0] += amount;
            if (from == outside && to == inside) cross[0] -= amount;
        };

        for (int tick = 0; tick < 400; tick++) {
            if (fan) wind.step(topology, List.of(FAN), List.of());
            volume.step(terrain, VaporVolume.Erosion.CLEAR, List.of(), List.of(SOURCE), wind, tick >= 200 ? meter : null);
        }
        return cross[0] / 200;
    }
}

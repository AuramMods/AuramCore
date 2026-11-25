package art.arcane.auram.field;

import art.arcane.auram.field.VoxelGrid.Position;
import art.arcane.auram.field.WindField.Source;
import org.junit.Test;

import java.util.List;

import static art.arcane.auram.field.VoxelGrid.VAPOR;
import static org.junit.Assert.*;

public class WindFieldTest {
    private static final Position ORIGIN = new Position(0, 0, 0);

    @Test
    public void flowsThroughABentDuctWithoutLeakingIntoAdjacentAir() {
        boolean[] blocks = new boolean[VAPOR.cells()];
        for (int x = 2; x <= 8; x++) open(blocks, x, 4, 2);
        for (int z = 2; z <= 12; z++) open(blocks, 8, 4, z);
        for (int z = 2; z <= 12; z++) open(blocks, 10, 4, z);
        open(blocks, 9, 5, 9);  // Opposite corner in the same coarse cell as the main duct.
        var topology = new WindTopology(ORIGIN, blocks);
        WindField field = new WindField();
        run(field, topology, List.of(new Source(new Position(2, 4, 2), .3f, 0, 0)), 50);
        assertTrue(speed(field, topology, 6, 4, 2) > .15f);
        assertTrue(field.component(topology.node(8, 4, 8), 2) > .03f);
        assertNotEquals(topology.node(8, 4, 8), topology.node(9, 5, 9));
        assertEquals(0, speed(field, topology, 9, 5, 9), 0);
        assertEquals(0, speed(field, topology, 10, 4, 8), 0);
    }

    @Test
    public void strongerSourcesReachFartherAndDecayWhenStopped() {
        boolean[] blocks = new boolean[VAPOR.cells()];
        for (int x = 2; x < 54; x++) open(blocks, x, 4, 2);
        var topology = new WindTopology(ORIGIN, blocks);
        WindField strong = new WindField();
        WindField weak = new WindField();
        var pos = new Position(2, 4, 2);
        run(strong, topology, List.of(new Source(pos, .8f, 0, 0)), 20);
        run(weak, topology, List.of(new Source(pos, .1f, 0, 0)), 20);
        assertTrue(speed(strong, topology, 24, 4, 2) > .2f);
        assertEquals(0, speed(weak, topology, 24, 4, 2), 0);
        run(strong, topology, List.of(), 300);
        assertFalse(strong.active());
    }

    @Test
    public void opposingSourcesCancelAndAlignedSourcesAreBounded() {
        boolean[] blocks = new boolean[VAPOR.cells()];
        for (int x = 2; x < 20; x++) open(blocks, x, 4, 2);
        var topology = new WindTopology(ORIGIN, blocks);
        WindField field = new WindField();
        var pos = new Position(8, 4, 2);
        Source right = new Source(pos, .8f, 0, 0);
        run(field, topology, List.of(right, right), 10);
        assertEquals(1.0f, speed(field, topology, 8, 4, 2), 1e-6f);
        run(field, topology, List.of(right, new Source(pos, -.8f, 0, 0)), 300);
        assertFalse(field.active());
    }

    @Test
    public void gustSurvivesATwoBlockShiftAndDisappearsAfterLeavingTheWindow() {
        boolean[] blocks = new boolean[VAPOR.cells()];
        for (int x = 0; x < 60; x++) open(blocks, x, 4, 2);
        var old = new WindTopology(ORIGIN, blocks);
        var moved = new WindTopology(new Position(2, 0, 0), blocks);
        WindField field = new WindField();
        var pos = new Position(21, 4, 2);
        var gust = new Source(pos, .8f, 0, 0);
        var reference = new WindField();
        reference.step(old, List.of(), List.of(gust));
        reference.step(old, List.of(), List.of());
        field.step(old, List.of(), List.of(gust));
        field.step(moved, List.of(), List.of());
        Position downwind = new Position(25, 4, 2);
        float expected = reference.component(old.node(downwind), 0);
        assertTrue(expected > .1f);
        assertEquals(expected, field.component(moved.node(downwind), 0), 1e-6f);
        var distant = new WindTopology(new Position(128, 0, 0), blocks);
        field.step(distant, List.of(), List.of());
        assertFalse(field.active());
    }

    private static void open(boolean[] blocks, int x, int y, int z) {
        blocks[VAPOR.index(x, y, z)] = true;
    }

    private static void run(WindField field, WindTopology topology, List<Source> sources, int ticks) {
        for (int t = 0; t < ticks; t++) field.step(topology, sources, List.of());
    }

    private static float speed(WindField field, WindTopology topology, int x, int y, int z) {
        return field.component(topology.node(x, y, z), 3);
    }
}

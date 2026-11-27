package art.arcane.auram.field;

import java.util.BitSet;
import java.util.Map;
import org.junit.Test;

import static art.arcane.auram.field.VoxelGrid.VAPOR;
import static art.arcane.auram.field.VoxelGrid.VAPOR_RADIUS;
import static org.junit.Assert.*;

public class VaporWindowTest {
    @Test
    public void guardBlocksCoverTheFullRadiusThroughNegativeSectionBoundaries() {
        for (int block = -116; block <= -80; block++) {
            var origin = VoxelGrid.vaporOriginAround(block, block, block);
            assertEquals(0, origin.x() & 1);
            assertTrue(block - VAPOR_RADIUS - origin.x() >= 1);
            assertTrue(origin.x() + VAPOR.side() - (block + 1 + VAPOR_RADIUS) >= 1);
        }
    }

    @Test
    public void occupancyCopiesPartialSectionsWithTheCorrectWorldOffsets() {
        var origin = new VoxelGrid.Position(-2, -2, -2);
        var solid = new BitSet();
        solid.set(VoxelGrid.SECTIONS.index(15, 15, 15));
        var occupancy = new VoxelOccupancy(origin, Map.of(
                new VoxelGrid.Position(-1, -1, -1), new VoxelOccupancy.SectionOccupancy(solid),
                new VoxelGrid.Position(4, 4, 4), new VoxelOccupancy.SectionOccupancy(new BitSet())));
        BitSet open = occupancy.openBlocks();
        assertTrue(open.get(VAPOR.index(0, 0, 0)));
        assertFalse(open.get(VAPOR.index(1, 1, 1)));
        assertTrue(open.get(VAPOR.index(67, 67, 67)));
        assertFalse(open.get(VAPOR.index(2, 2, 2)));
        assertEquals(new VoxelGrid.Position(16, 16, 16), VAPOR.lastSection(origin));
    }
}

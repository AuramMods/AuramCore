package art.arcane.auram.field;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;

import static art.arcane.auram.field.VoxelGrid.*;

/** Immutable fine occupancy for a field window. Missing sections behave as solid terrain. */
public record VoxelOccupancy(Position origin, Map<Position, SectionOccupancy> sections) {
    public VoxelOccupancy {
        sections = Map.copyOf(sections);
    }

    public boolean[] openBlocks() {
        int side = VAPOR.side();
        boolean[] open = new boolean[VAPOR.cells()];
        for (var entry : sections.entrySet()) {
            Position section = entry.getKey();
            SectionOccupancy occupancy = entry.getValue();
            int x0 = section.x() * SECTION - origin.x();
            int y0 = section.y() * SECTION - origin.y();
            int z0 = section.z() * SECTION - origin.z();
            int fromX = Math.max(0, -x0), toX = Math.min(SECTION, side - x0);
            int fromY = Math.max(0, -y0), toY = Math.min(SECTION, side - y0);
            int fromZ = Math.max(0, -z0), toZ = Math.min(SECTION, side - z0);
            if (fromX >= toX || fromY >= toY || fromZ >= toZ) continue;
            boolean air = occupancy.occupied.isEmpty();
            for (int z = fromZ; z < toZ; z++) {
                for (int y = fromY; y < toY; y++) {
                    int row = VAPOR.index(x0 + fromX, y0 + y, z0 + z);
                    if (air) {
                        Arrays.fill(open, row, row + toX - fromX, true);
                    } else {
                        for (int x = fromX; x < toX; x++) {
                            open[row + x - fromX] = !occupancy.blocked(x, y, z);
                        }
                    }
                }
            }
        }
        return open;
    }

    public record SectionOccupancy(BitSet occupied) {
        public SectionOccupancy {
            occupied = (BitSet) occupied.clone();
        }

        boolean blocked(int x, int y, int z) {
            return occupied.get(SECTIONS.index(x, y, z));
        }

        public SectionOccupancy with(int x, int y, int z, boolean blocked) {
            int index = SECTIONS.index(x, y, z);
            if (occupied.get(index) == blocked) return this;
            BitSet updated = (BitSet) occupied.clone();
            updated.set(index, blocked);
            return new SectionOccupancy(updated);
        }

        @Override
        public BitSet occupied() {
            return (BitSet) occupied.clone();
        }
    }
}

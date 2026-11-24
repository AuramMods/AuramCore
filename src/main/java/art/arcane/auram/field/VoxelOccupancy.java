package art.arcane.auram.field;

import java.util.BitSet;
import java.util.Map;

import static art.arcane.auram.field.VoxelGrid.*;

/** Immutable fine occupancy for a field window. Missing sections behave as solid terrain. */
public record VoxelOccupancy(Position origin, Map<Position, SectionOccupancy> sections) {
    public VoxelOccupancy {
        sections = Map.copyOf(sections);
    }

    public boolean[] openBlocks() {
        boolean[] open = new boolean[BLOCKS.cells()];
        for (var entry : sections.entrySet()) {
            Position section = entry.getKey();
            SectionOccupancy occupancy = entry.getValue();
            int x0 = section.x() * SECTION - origin.x();
            int y0 = section.y() * SECTION - origin.y();
            int z0 = section.z() * SECTION - origin.z();
            if (!BLOCKS.contains(x0, y0, z0)) continue;
            for (int z = 0; z < SECTION; z++) {
                for (int y = 0; y < SECTION; y++) {
                    for (int x = 0; x < SECTION; x++) {
                        open[BLOCKS.index(x0 + x, y0 + y, z0 + z)] = !occupancy.blocked(x, y, z);
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

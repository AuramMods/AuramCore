package art.arcane.auram.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class WellFlatness {
    private WellFlatness() { }

    @FunctionalInterface
    public interface ColumnTest {
        boolean matches(int dx, int dz);
    }

    public static boolean test(Structure.GenerationContext context, BlockPos center, int radius) {
        return scan(radius, (dx, dz) -> {
            NoiseColumn column = context.chunkGenerator().getBaseColumn(center.getX() + dx, center.getZ() + dz,
                    context.heightAccessor(), context.randomState());
            return column.getBlock(center.getY()).isAir() && !column.getBlock(center.getY() + 1).isAir();
        });
    }

    /** Exact Malum predicate for radii whose square scan fits an int. */
    public static boolean scan(int radius, ColumnTest column) {
        int required = radius * radius * 2;
        // The original loops are empty for zero/negative radii; preserve even its
        // integer-overflow behavior for the final threshold in that case.
        if (radius <= 0) return 0 >= required;
        int remaining = radius * radius * 4;
        int matches = 0;
        for (int dx = -radius; dx < radius; dx++) {
            for (int dz = -radius; dz < radius; dz++) {
                if (column.matches(dx, dz)) matches++;
                remaining--;
                if (matches >= required) return true;
                if (matches + remaining < required) return false;
            }
        }
        return matches >= required;
    }
}

package art.arcane.auram.vapor;

import net.minecraft.core.BlockPos;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class DeathVaporSpreadTest {
    @Test
    public void healthBudgetIsCappedAndMatchesRequestedSizes() {
        assertEquals(1, DeathVaporSpread.attempts(5));
        assertEquals(2, DeathVaporSpread.attempts(20));
        assertEquals(3, DeathVaporSpread.attempts(40));
        assertEquals(4, DeathVaporSpread.attempts(100));
        assertEquals(4, DeathVaporSpread.attempts(400));
        for (int health = 2; health <= 100; health++) {
            assertTrue(DeathVaporSpread.attempts(health) >= DeathVaporSpread.attempts(health - 1));
        }
    }

    @Test
    public void openFloorUsesTheFullBudgetWithoutRepeatingCells() {
        List<BlockPos> puffs = new ArrayList<>();
        DeathVaporSpread.spread(BlockPos.ZERO, DeathVaporSpread.attempts(100), pos -> pos.getY() >= 0, puffs::add);
        assertEquals(4, puffs.size());
        assertEquals(4, new HashSet<>(puffs).size());
        assertTrue(puffs.stream().allMatch(pos -> pos.getY() == 0));
    }

    @Test
    public void wallsSpendAttemptsAndStopThatBranch() {
        var wall = BlockPos.ZERO.north();
        List<BlockPos> puffs = new ArrayList<>();
        DeathVaporSpread.spread(BlockPos.ZERO, 4, pos -> pos.getY() >= 0 && !pos.equals(wall), puffs::add);
        assertEquals(Set.of(BlockPos.ZERO, BlockPos.ZERO.east(), BlockPos.ZERO.south()), new HashSet<>(puffs));
        assertEquals(3, puffs.size());
    }

    @Test
    public void dropsToTheFloorBeforeSpreading() {
        List<BlockPos> puffs = new ArrayList<>();
        DeathVaporSpread.spread(new BlockPos(0, 1, 0), 4, pos -> pos.getY() >= 0, puffs::add);
        assertEquals(3, puffs.size());
        assertEquals(BlockPos.ZERO, puffs.get(0));
        assertTrue(puffs.stream().allMatch(pos -> pos.getY() == 0));
    }
}

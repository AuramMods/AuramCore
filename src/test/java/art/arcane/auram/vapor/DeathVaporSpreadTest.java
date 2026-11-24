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
        assertEquals(2, DeathVaporSpread.attempts(5));
        assertEquals(8, DeathVaporSpread.attempts(20));
        assertEquals(16, DeathVaporSpread.attempts(100));
        assertEquals(16, DeathVaporSpread.attempts(400));
        for (int health = 2; health <= 100; health++) {
            assertTrue(DeathVaporSpread.attempts(health) >= DeathVaporSpread.attempts(health - 1));
        }
    }

    @Test
    public void openFloorUsesTheFullBudgetWithoutRepeatingCells() {
        List<BlockPos> puffs = new ArrayList<>();
        DeathVaporSpread.spread(BlockPos.ZERO, 16, pos -> pos.getY() >= 0, puffs::add);
        assertEquals(16, puffs.size());
        assertEquals(16, new HashSet<>(puffs).size());
        assertTrue(puffs.stream().allMatch(pos -> pos.getY() == 0));
    }

    @Test
    public void wallsSpendAttemptsAndStopThatBranch() {
        var walls = Set.of(BlockPos.ZERO.north(), BlockPos.ZERO.east());
        List<BlockPos> puffs = new ArrayList<>();
        DeathVaporSpread.spread(BlockPos.ZERO, 5, pos -> pos.getY() >= 0 && !walls.contains(pos), puffs::add);
        assertEquals(Set.of(BlockPos.ZERO, BlockPos.ZERO.south(), BlockPos.ZERO.west()), new HashSet<>(puffs));
        assertEquals(3, puffs.size());
    }

    @Test
    public void dropsToTheFloorBeforeSpreading() {
        List<BlockPos> puffs = new ArrayList<>();
        DeathVaporSpread.spread(new BlockPos(0, 3, 0), 8, pos -> pos.getY() >= 0, puffs::add);
        assertEquals(5, puffs.size());
        assertEquals(BlockPos.ZERO, puffs.get(0));
        assertTrue(puffs.stream().allMatch(pos -> pos.getY() == 0));
    }
}

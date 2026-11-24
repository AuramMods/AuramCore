package art.arcane.auram.vapor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class DeathVaporSpread {
    private static final int MAX_ATTEMPTS = 16;
    private static final float HEALTH_AT_LIMIT = 100.0f;
    private static final double MAX_EXPONENTIAL = 1.0 - Math.pow(0.5, HEALTH_AT_LIMIT / 20.0);

    private DeathVaporSpread() {}

    public static void emit(ServerLevel level, BlockPos origin, float maxHealth) {
        spread(origin, attempts(maxHealth),
                pos -> !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos) && level.isEmptyBlock(pos),
                pos -> Vapor.pulse(level, pos, 1.0f));
    }

    static int attempts(float maxHealth) {
        if (maxHealth >= HEALTH_AT_LIMIT) return MAX_ATTEMPTS;
        double scaled = MAX_ATTEMPTS * (1.0 - Math.pow(0.5, maxHealth / 20.0)) / MAX_EXPONENTIAL;
        return Math.max(2, (int) scaled);
    }

    static void spread(BlockPos origin, int budget, Predicate<BlockPos> isAir, Consumer<BlockPos> emit) {
        var pending = new ArrayDeque<BlockPos>();
        var visited = new HashSet<BlockPos>();
        pending.add(origin);
        visited.add(origin);
        BlockPos lowestAir = null;
        boolean emitted = false;
        for (int attempt = 0; attempt < budget && !pending.isEmpty(); attempt++) {
            BlockPos pos = pending.removeFirst();
            if (!isAir.test(pos)) {
                // A blocked death cell can vent sideways. Other walls end that branch.
                if (pos.equals(origin)) expandOut(pos, pending, visited);
                continue;
            }

            if (lowestAir == null || pos.getY() < lowestAir.getY()) lowestAir = pos;
            BlockPos below = pos.below();
            if (isAir.test(below)) {
                if (visited.add(below)) pending.addFirst(below);
                continue;
            }

            emit.accept(pos);
            emitted = true;
            expandOut(pos, pending, visited);
        }
        // Far above the floor, use the lowest air cell reached within the same budget.
        if (!emitted && lowestAir != null) emit.accept(lowestAir);
    }

    private static void expandOut(BlockPos pos, ArrayDeque<BlockPos> pending, Set<BlockPos> visited) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(direction);
            if (visited.add(neighbor)) pending.addLast(neighbor);
        }
    }
}

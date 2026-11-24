package art.arcane.auram.smoke;

import art.arcane.auram.mixin.minecraft.NoiseInterpolatorSliceAccess;
import art.arcane.auram.smoke.mixin.NoiseSliceCompareAccess;
import art.arcane.auram.util.TerrainBatch;
import art.arcane.auram.util.TerrainCell;
import com.google.gson.GsonBuilder;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

public final class BoundedSliceSmoke {
    private static final int WARMUPS = 5;
    private static final int PAIRS = 25;
    private static final int BATCH = 16;

    private BoundedSliceSmoke() {}

    public static void run(ServerLevel level) throws Exception {
        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        var shape = generator.generatorSettings().value().noiseSettings().clampToHeightAccessor(level);
        if (shape.minY() != -64 || shape.height() != 1088) {
            throw new AssertionError("Expected the Auram Overworld height, got " + shape);
        }
        int states = 0;
        int densities = 0;
        var noise = TerrainCell.class.getDeclaredField("noise");
        noise.setAccessible(true);
        int boundedCases = 0;
        int expansions = 0;
        for (long seed : new long[]{104729, 17, 1907}) {
            RandomState state = state(level, generator, seed);
            int index = 0;
            for (int top : new int[]{-64, -61, -57, -56, -1, 0, 62, 63, 64, 65, 127, 128, 255,
                    512, 1015, 1016, 1023, 1024, 1600}) {
                int x = (index * 37 - 351), z = (index * -67 + 166);
                index++;
                try (TerrainCell full = open(level, generator, state, x, z, Integer.MAX_VALUE);
                     TerrainCell prefix = open(level, generator, state, x, z, top)) {
                    int rows = (Math.min(top, 1023) - shape.minY()) / shape.getCellHeight() + 2;
                    densities += compareRows((NoiseSliceCompareAccess) noise.get(full),
                            (NoiseSliceCompareAccess) noise.get(prefix), rows);
                    if (!prefix.coversThrough(top)) throw new AssertionError("Incomplete requested range at " + top);
                    if (!prefix.coversThrough(Integer.MAX_VALUE)) boundedCases++;
                    for (int y = shape.minY(); y <= Math.min(top, 1023); y++) {
                        if (full.state(x, y, z) != prefix.state(x, y, z)) {
                            throw new AssertionError("Shifted state at seed=" + seed + " x=" + x + " y=" + y + " z=" + z);
                        }
                        states++;
                    }
                    if (!prefix.state(x, 1024, z).isAir() || !prefix.state(x, -65, z).isAir()) {
                        throw new AssertionError("Outside-world state changed");
                    }
                }
                try (var batch = new TerrainBatch(generator, level, state, false)) {
                    var prefix = batch.borrowCell(x, z, top);
                    var value = prefix.state(x, Math.min(top, 1023), z);
                    var expanded = batch.borrowCell(x, z);
                    if (!expanded.coversThrough(Integer.MAX_VALUE)
                            || expanded.state(x, Math.min(top, 1023), z) != value) {
                        throw new AssertionError("A later full-column query changed an earlier result");
                    }
                    if (prefix != expanded) expansions++;
                }
            }
        }
        if (boundedCases == 0 || expansions == 0) throw new AssertionError("No bounded/expanded samplers exercised");

        List<Pair> pairs = new ArrayList<>();
        for (int run = -WARMUPS; run < PAIRS; run++) {
            // Same inputs and a fresh seeded world-noise state for each arm.
            // Alternate order to diminish effects of the preceding arm.
            var stateA = state(level, generator, level.getSeed());
            var stateB = state(level, generator, level.getSeed());
            Timing full, prefix;
            if ((run & 1) == 0) {
                full = time(level, generator, stateA, run, false);
                prefix = time(level, generator, stateB, run, true);
            } else {
                prefix = time(level, generator, stateB, run, true);
                full = time(level, generator, stateA, run, false);
            }
            if (!Arrays.equals(full.foundY, prefix.foundY)) {
                throw new AssertionError("Fossil-style surface search mismatch in batch " + run);
            }
            if (run >= 0) pairs.add(new Pair(full, prefix));
        }
        Files.writeString(Path.of("bounded-slice.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "bit_identical_density_values", densities, "equivalent_block_states", states, "bounded_cases", boundedCases, "expanded_cases", expansions,
                "warmup_pairs", WARMUPS, "queries_per_arm", PAIRS * BATCH, "pairs", pairs)) + "\n");
    }

    private static int compareRows(NoiseSliceCompareAccess full, NoiseSliceCompareAccess prefix, int rows) {
        var fullInterpolators = full.auramSmoke$interpolators();
        var prefixInterpolators = prefix.auramSmoke$interpolators();
        if (fullInterpolators.size() != prefixInterpolators.size()) {
            throw new AssertionError("Interpolator count changed");
        }
        int compared = 0;
        for (int i = 0; i < fullInterpolators.size(); i++) {
            var a = (NoiseInterpolatorSliceAccess) fullInterpolators.get(i);
            var b = (NoiseInterpolatorSliceAccess) prefixInterpolators.get(i);
            compared += compareRows(a.auram$slice0(), b.auram$slice0(), rows);
            compared += compareRows(a.auram$slice1(), b.auram$slice1(), rows);
        }
        return compared;
    }

    private static int compareRows(double[][] full, double[][] prefix, int rows) {
        for (int z = 0; z < full.length; z++) {
            for (int y = 0; y < rows; y++) {
                if (Double.doubleToRawLongBits(full[z][y]) != Double.doubleToRawLongBits(prefix[z][y])) {
                    throw new AssertionError("Slice density changed at row " + y);
                }
            }
        }
        return full.length * rows;
    }

    private static RandomState state(ServerLevel level, NoiseBasedChunkGenerator generator, long seed) {
        return RandomState.create(generator.generatorSettings().value(),
                level.registryAccess().lookupOrThrow(Registries.NOISE), seed);
    }

    private static TerrainCell open(ServerLevel level, NoiseBasedChunkGenerator generator,
                                    RandomState state, int x, int z, int top) {
        var terrain = TerrainCell.open(generator, x, z, level, state, top);
        if (terrain == null) throw new AssertionError("Fixture did not supply complete interpolation cells");
        return terrain;
    }

    private static Timing time(ServerLevel level, NoiseBasedChunkGenerator generator,
                               RandomState state, int run, boolean bounded) {
        var clock = ManagementFactory.getThreadMXBean();
        int sea = generator.getSeaLevel();
        int[] found = new int[BATCH];
        long wall = System.nanoTime();
        long cpu = clock.getCurrentThreadCpuTime();
        for (int column = 0; column < BATCH; column++) {
            int index = run * BATCH + column;
            int x = -431 + 31 * index, z = 195 - 23 * index;
            int upper = sea + 1 + Math.floorMod(index * 113, 930);
            try (var cell = open(level, generator, state, x, z, bounded ? upper : Integer.MAX_VALUE)) {
                int y = Math.min(upper, cell.maxY());
                var pos = new BlockPos.MutableBlockPos(x, y, z);
                var above = cell.state(x, y, z);
                while (y > sea) {
                    var floor = cell.state(x, --y, z);
                    if (above.isAir() && (floor.is(Blocks.SOUL_SAND)
                            || floor.isFaceSturdy(EmptyBlockGetter.INSTANCE, pos.setY(y), Direction.UP))) break;
                    above = floor;
                }
                found[column] = y;
            }
        }
        return new Timing(System.nanoTime() - wall, clock.getCurrentThreadCpuTime() - cpu, found);
    }

    private record Timing(long wall_ns, long cpu_ns, int[] foundY) {}
    private record Pair(Timing full, Timing prefix) {}
}

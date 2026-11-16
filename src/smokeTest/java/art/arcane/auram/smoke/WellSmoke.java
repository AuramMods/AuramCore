package art.arcane.auram.smoke;

import art.arcane.auram.util.WellFlatness;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class WellSmoke {
    public static void run(MinecraftServer server) throws Exception {
        int patterns = 0;
        Random random = new Random(8675309);
        for (int radius = 0; radius <= 16; radius++) {
            int total = 4 * radius * radius;
            for (int trial = 0; trial < 300; trial++) {
                boolean[] values = new boolean[total];
                int matches = 0;
                for (int i = 0; i < total; i++) {
                    values[i] = trial == 0 || (trial > 1 && random.nextBoolean());
                    if (values[i]) matches++;
                }
                final int side = radius * 2;
                final int r = radius;
                int[] visited = {0};
                boolean result = WellFlatness.scan(radius, (x, z) -> {
                    int index = (x + r) * side + z + r;
                    check(index == visited[0]++, "scan retains the original traversal prefix");
                    return values[index];
                });
                check(result == (matches >= radius * radius * 2), "threshold result matches original full scan");
                patterns++;
            }
        }
        for (int radius : new int[]{-1, -8, Integer.MIN_VALUE}) {
            check(WellFlatness.scan(radius, (x,z) -> { throw new AssertionError("empty scan evaluated a column"); })
                    == (0 >= radius * radius * 2), "negative-radius original behavior retained");
        }

        ServerLevel level = server.overworld();
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        Structure well = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.tryBuild("malum", "weeping_well"));
        Method patched = well.getClass().getMethod("isSufficientlyFlat", Structure.GenerationContext.class, BlockPos.class, int.class);
        long oldCalls = 0, newCalls = 0;
        List<Double> oldTimes = new ArrayList<>(), newTimes = new ArrayList<>();
        int cases = 0, candidateCases = 0;
        Method validY = well.getClass().getMethod("getValidY", NoiseColumn.class);
        for (long seed : new long[]{104729, 17, 1907}) {
            RandomState state = RandomState.create(generator.generatorSettings().value(),
                    level.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            for (BlockPos center : new BlockPos[]{new BlockPos(8,-32,8), new BlockPos(72,-45,-40),
                    new BlockPos(-88,64,120), new BlockPos(200,-20,-160)}) {
                Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), generator,
                        generator.getBiomeSource(), state, server.getStructureManager(), seed, new ChunkPos(center), level, b -> true);
                int candidateY = (int) validY.invoke(well, generator.getBaseColumn(center.getX(), center.getZ(), level, state));
                if (candidateY >= -50) {
                    center = center.atY(candidateY);
                    candidateCases++;
                }
                // Warm both execution paths before timing. Neither consumes the
                // context RNG; compare its next value as well as the result.
                original(context, center, 8);
                patched.invoke(well, context, center, 8);
                for (int pair = 0; pair < 3; pair++) {
                    boolean a = false, b = false;
                    long ra = 0, rb = 0;
                    for (int order = 0; order < 2; order++) {
                        boolean reference = (order + pair) % 2 == 0;
                        context.random().setSeed(123456789);
                        ColumnProbe.start();
                        long start = System.nanoTime();
                        boolean value = reference ? original(context, center, 8) : (boolean) patched.invoke(well, context, center, 8);
                        double millis = (System.nanoTime()-start)/1e6;
                        long calls = ColumnProbe.stop();
                        long next = context.random().nextLong();
                        if (reference) { a=value;ra=next;oldCalls+=calls;oldTimes.add(millis);check(calls==256,"original scans all columns"); }
                        else { b=value;rb=next;newCalls+=calls;newTimes.add(millis);check(calls<=256,"patched query count cannot increase"); }
                    }
                    check(a == b, "fixed-seed terrain eligibility matches");
                    check(ra == rb, "worldgen RNG sequence is unchanged");
                }
                cases++;
                LogUtils.getLogger().info("[AURAM WELL] verified seed {} center {}",seed,center);
            }
        }
        check(candidateCases > 0, "fixed-seed cases include actual well candidate heights");
        check(newCalls < oldCalls, "production Malum mixin reduces terrain-column queries");
        oldTimes.sort(Double::compareTo);newTimes.sort(Double::compareTo);
        String result = "{\"boolean_patterns\":"+patterns+",\"fixed_seed_cases\":"+cases+",\"actual_candidate_cases\":"+candidateCases+",\"paired_calls\":"+oldTimes.size()
                +",\"original_columns\":"+oldCalls+",\"patched_columns\":"+newCalls
                +",\"original_median_ms\":"+oldTimes.get(oldTimes.size()/2)+",\"patched_median_ms\":"+newTimes.get(newTimes.size()/2)+"}\n";
        Files.writeString(Path.of("well-benchmark.json"), result);
        LogUtils.getLogger().info("[AURAM WELL] PASS {}", result);
    }

    private static boolean original(Structure.GenerationContext context, BlockPos center, int radius) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x=-radius;x<radius;x++) for(int z=-radius;z<radius;z++) positions.add(center.offset(x,0,z));
        int matches=0;
        for(BlockPos pos:positions) {
            NoiseColumn column=context.chunkGenerator().getBaseColumn(pos.getX(),pos.getZ(),context.heightAccessor(),context.randomState());
            if(column.getBlock(pos.getY()).isAir() && !column.getBlock(pos.above().getY()).isAir()) matches++;
        }
        return matches>=radius*radius*2;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

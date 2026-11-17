package art.arcane.auram.smoke;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.NetherFossilStructure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class FossilSmoke {
    public static void run(MinecraftServer server) throws Exception {
        ServerLevel level = server.getLevel(Level.NETHER);
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        NetherFossilStructure standard = (NetherFossilStructure) level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.tryBuild("minecraft", "nether_fossil"));
        int sea = generator.getSeaLevel();
        List<NetherFossilStructure> variants = new ArrayList<>();
        variants.add(standard);
        for (int height : new int[]{-100, sea-1, sea, sea+1, 128, 512}) {
            variants.add(new NetherFossilStructure(standard.getModifiedStructureSettings(), ConstantHeight.of(VerticalAnchor.absolute(height))));
        }
        long originalColumns = 0, patchedColumns = 0, normalOriginal = 0, normalPatched = 0;
        int cases = 0, accepted = 0, rejected = 0;
        List<Double> oldTimes = new ArrayList<>(), newTimes = new ArrayList<>();
        for (long seed : new long[]{104729, 17, 1907}) {
            RandomState state = RandomState.create(generator.generatorSettings().value(),
                    level.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            for (int variant = 0; variant < variants.size(); variant++) {
                NetherFossilStructure structure = variants.get(variant);
                for (int i = 0; i < (variant == 0 ? 256 : 16); i++) {
                    ChunkPos chunk = new ChunkPos(i*7-48, i*11-80);
                    // Alternate A/B order. Compare point eligibility, exact point,
                    // and the next worldgen RNG value after the actual method.
                    for (int pair = 0; pair < 3; pair++) {
                        Optional<BlockPos> a = null, b = null;
                        long randomA = 0, randomB = 0;
                        for (int order = 0; order < 2; order++) {
                            boolean reference = (pair + order) % 2 == 0;
                            Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), generator,
                                    generator.getBiomeSource(), state, server.getStructureManager(), seed, chunk, level, biome -> true);
                            ColumnProbe.start();
                            long start = System.nanoTime();
                            Optional<BlockPos> point = reference ? original(structure, context)
                                    : structure.findGenerationPoint(context).map(Structure.GenerationStub::position);
                            double millis = (System.nanoTime()-start)/1e6;
                            long columns = ColumnProbe.stop();
                            long next = context.random().nextLong();
                            if (reference) {
                                a=point;randomA=next;originalColumns+=columns;oldTimes.add(millis);
                                check(columns==1,"reference queries exactly one terrain column");
                                if(variant==0) normalOriginal+=columns;
                            } else {
                                b=point;randomB=next;patchedColumns+=columns;newTimes.add(millis);
                                if(variant==0) normalPatched+=columns;
                                check(columns<=1,"candidate cannot add terrain queries");
                            }
                        }
                        check(a.equals(b),"eligibility and exact fossil position match");
                        check(randomA==randomB,"fossil RNG sequence matches");
                        if(pair==0) { if(a.isPresent()) accepted++; else rejected++; }
                    }
                    cases++;
                }
            }
        }
        check(accepted>0 && rejected>0,"both successful and failed fossil searches covered");
        check(normalPatched<normalOriginal,"vanilla height distribution actually saves queries");
        Collections.sort(oldTimes); Collections.sort(newTimes);
        String result = String.format(java.util.Locale.ROOT,
                "{\"fixed_seed_cases\":%d,\"accepted\":%d,\"rejected\":%d,\"paired_calls\":%d,\"original_columns\":%d,\"patched_columns\":%d,\"vanilla_distribution_original_columns\":%d,\"vanilla_distribution_patched_columns\":%d,\"original_median_ms\":%.6f,\"patched_median_ms\":%.6f}",
                cases,accepted,rejected,oldTimes.size(),originalColumns,patchedColumns,normalOriginal,normalPatched,
                oldTimes.get(oldTimes.size()/2),newTimes.get(newTimes.size()/2));
        Files.writeString(Path.of("fossil-benchmark.json"),result+"\n");
        LogUtils.getLogger().info("[AURAM FOSSIL] PASS {}",result);
    }

    /** Original Forge 47.4.10 search, returning the position before piece generation. */
    private static Optional<BlockPos> original(NetherFossilStructure structure, Structure.GenerationContext context) {
        WorldgenRandom random = context.random();
        int x = context.chunkPos().getMinBlockX()+random.nextInt(16);
        int z = context.chunkPos().getMinBlockZ()+random.nextInt(16);
        int sea = context.chunkGenerator().getSeaLevel();
        int height = structure.height.sample(random, new WorldGenerationContext(context.chunkGenerator(),context.heightAccessor()));
        NoiseColumn column = context.chunkGenerator().getBaseColumn(x,z,context.heightAccessor(),context.randomState());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x,height,z);
        while(height>sea) {
            BlockState top = column.getBlock(height);
            height--;
            BlockState floor = column.getBlock(height);
            if(top.isAir() && (floor.is(Blocks.SOUL_SAND) || floor.isFaceSturdy(EmptyBlockGetter.INSTANCE,pos.setY(height),Direction.UP))) break;
        }
        return height<=sea ? Optional.empty() : Optional.of(new BlockPos(x,height,z));
    }
    private static void check(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
}

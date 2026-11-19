package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public final class TwigsObeliskSmoke {
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static final ResourceLocation OBELISK = new ResourceLocation("twigs", "bloodstone_obelisk");

    public static void run(MinecraftServer server) throws Exception {
        ServerLevel nether = server.getLevel(Level.NETHER);
        Structure structure = nether.registryAccess().registryOrThrow(Registries.STRUCTURE).get(OBELISK);
        var method = structure.getClass().getMethod("canGenerate", Structure.GenerationContext.class, BlockPos.class);
        var generator = (NoiseBasedChunkGenerator) nether.getChunkSource().getGenerator();
        var pieceContext = StructurePieceSerializationContext.fromLevel(nether);
        int radius = Integer.getInteger("auram.smoke.twigsSearchRadius", 48);
        List<Attempt> results = new ArrayList<>();
        for (long seed : new long[]{104729, 17, 1907}) {
            RandomState state = RandomState.create(generator.generatorSettings().value(),
                    nether.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            for (int i = -radius; i < radius; i++) {
                ChunkPos pos = new ChunkPos(i, i * 3 - 41);
                var context = context(nether, generator, state, seed, pos);
                BlockPos center = pos.getMiddleBlockPosition(0);
                Search search = search(method, context, center);
                var stub = structure.findValidGenerationPoint(context);
                if (search.accepted() != stub.isPresent()) {
                    throw new AssertionError("Obelisk decision and stub differ: " + pos);
                }
                String placement = stub.map(value -> value.position().toShortString() + ':'
                        + TerrainQuerySmoke.canonical(value.getPiecesBuilder().build().save(pieceContext))).orElse("");
                results.add(new Attempt(seed, center.getX(), center.getZ(), search, placement, context.random().nextLong()));
                if (i % 8 == 0) {
                    BlockPos seam = pos.getWorldPosition();
                    var seamContext = context(nether, generator, state, seed, pos);
                    results.add(new Attempt(seed, seam.getX(), seam.getZ(), search(method, seamContext, seam),
                            "", seamContext.random().nextLong()));
                }
            }
        }
        if (results.stream().allMatch(attempt -> attempt.search().accepted())
                || results.stream().noneMatch(attempt -> attempt.search().accepted())) {
            throw new AssertionError("Nether cases must include both placed and rejected attempts");
        }

        // A custom generator can observe subsequent column probes. Preserve them.
        var registry = nether.registryAccess();
        var settings = FlatLevelGeneratorSettings.getDefault(registry.lookupOrThrow(Registries.BIOME),
                registry.lookupOrThrow(Registries.STRUCTURE_SET), registry.lookupOrThrow(Registries.PLACED_FEATURE));
        int[] customCalls = {0};
        ChunkGenerator custom = new FlatLevelSource(settings) {
            @Override
            public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState state) {
                customCalls[0]++;
                BlockState[] blocks = new BlockState[128];
                Arrays.fill(blocks, Blocks.AIR.defaultBlockState());
                blocks[79] = Blocks.STONE.defaultBlockState();
                return new NoiseColumn(0, blocks);
            }
        };
        var customContext = context(nether, custom, nether.getChunkSource().randomState(), nether.getSeed(), new ChunkPos(-1, 0));
        if (!(boolean) method.invoke(null, customContext, BlockPos.ZERO) || customCalls[0] != 121) {
            throw new AssertionError("Custom generator did not retain all 121 probes");
        }

        var clipped = new Structure.GenerationContext(registry, generator, generator.getBiomeSource(),
                nether.getChunkSource().randomState(), server.getStructureManager(), nether.getSeed(),
                new ChunkPos(0, 0), LevelHeightAccessor.create(0, 9), biome -> true);
        Search clippedSearch = search(method, clipped, BlockPos.ZERO);
        if (clippedSearch.accepted() || clippedSearch.columns() != 121) {
            throw new AssertionError("Clipped accessor did not retain vanilla column probes");
        }

        var set = registry.registryOrThrow(Registries.STRUCTURE_SET).get(OBELISK);
        var placement = (RandomSpreadStructurePlacement) set.placement();
        boolean orderFeatures = Boolean.getBoolean("auram.smoke.orderTwigsFeatures");
        List<Generated> chunks = new ArrayList<>();
        for (int region = -4; region < 4; region++) {
            ChunkPos pos = placement.getPotentialStructureChunk(nether.getSeed(), region * placement.spacing(), -placement.spacing());
            chunks.add(generate(nether, structure, pos, orderFeatures));
        }
        if (chunks.stream().noneMatch(Generated::obelisk)) {
            // A worldgen datapack can move the Obelisk's biomes. Find one
            // through actual structure placement in a wider seeded area.
            ChunkPos additional = findObelisk(nether, structure, placement);
            chunks.add(generate(nether, structure, additional, orderFeatures));
        }
        if (chunks.stream().noneMatch(Generated::obelisk)) throw new AssertionError("FULL run lost an Obelisk start");

        Files.writeString(Path.of("twigs-obelisk-benchmark.json"), new GsonBuilder().setPrettyPrinting().create()
                .toJson(Map.of("searches", results, "custom_columns", customCalls[0], "full_chunks", chunks)) + "\n");
    }

    private static ChunkPos findObelisk(ServerLevel nether, Structure structure, RandomSpreadStructurePlacement placement) {
        for (int z = -4; z <= 4; z++) {
            for (int x = -4; x <= 4; x++) {
                ChunkPos pos = placement.getPotentialStructureChunk(nether.getSeed(), x * placement.spacing(), z * placement.spacing());
                var chunk = nether.getChunk(pos.x, pos.z, ChunkStatus.STRUCTURE_STARTS, true);
                var start = chunk.getStartForStructure(structure);
                if (start != null && start.isValid()) return pos;
            }
        }
        throw new AssertionError("No natural Obelisk placement found in the fixture's search area");
    }

    private static Generated generate(ServerLevel nether, Structure structure, ChunkPos pos, boolean orderFeatures) throws Exception {
        long start = System.nanoTime();
        if (orderFeatures) {
            // Features can write into neighboring chunks. Run their normal
            // generation stage in the same order in each comparison arm.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    nether.getChunk(pos.x + dx, pos.z + dz, ChunkStatus.FEATURES, true);
                }
            }
        }
        var chunk = nether.getChunk(pos.x, pos.z);
        long elapsed = System.nanoTime() - start;
        var obelisk = chunk.getStartForStructure(structure);
        CompoundTag saved = ChunkSerializer.write(nether, chunk);
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        for (String key : List.of("sections", "block_entities", "structures")) {
            hash.update(TerrainQuerySmoke.canonical(saved.get(key)).getBytes(StandardCharsets.UTF_8));
        }
        return new Generated(pos.x, pos.z, obelisk != null && obelisk.isValid(),
                HexFormat.of().formatHex(hash.digest()), elapsed);
    }

    private static Search search(Method method, Structure.GenerationContext context, BlockPos pos) throws Exception {
        ColumnProbe.start();
        long startCpu = THREADS.getCurrentThreadCpuTime();
        long start = System.nanoTime();
        boolean accepted;
        long elapsed, cpu, columns;
        try {
            accepted = (boolean) method.invoke(null, context, pos);
            elapsed = System.nanoTime() - start;
            cpu = THREADS.getCurrentThreadCpuTime() - startCpu;
        } finally {
            columns = ColumnProbe.stop();
        }
        return new Search(accepted, columns, elapsed, cpu);
    }

    private static Structure.GenerationContext context(ServerLevel level, ChunkGenerator generator, RandomState state,
                                                       long seed, ChunkPos pos) {
        return new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(), state,
                level.getServer().getStructureManager(), seed, pos, level, biome -> true);
    }

    private record Search(boolean accepted, long columns, long nanos, long cpuNanos) {
    }

    private record Attempt(long seed, int x, int z, Search search, String placement, long nextRandom) {
    }

    private record Generated(int x, int z, boolean obelisk, String fingerprint, long nanos) {
    }
}

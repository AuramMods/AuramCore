package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public final class LithoStartParitySmoke {
    private static final ThreadLocal<List<Checked>> ATTEMPTS = new ThreadLocal<>();

    public static void run(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        List<Callable<Result>> cases = new ArrayList<>();
        for (long seed : new long[]{104729, 17, 1907}) {
            var registry = level.registryAccess();
            var random = RandomState.create(generator.generatorSettings().value(), registry.lookupOrThrow(Registries.NOISE), seed);
            var state = generator.createState(registry.lookupOrThrow(Registries.STRUCTURE_SET), random, seed);
            state.ensureStructuresGenerated();
            for (int x = -12; x < 12; x++) {
                for (int z = 72; z < 96; z++) {
                    ChunkPos pos = new ChunkPos(x, z);
                    cases.add(() -> generate(level, generator, state, pos));
                }
            }
        }
        LithoStartProbe.start();
        List<Result> results = new ArrayList<>();
        var workers = Executors.newFixedThreadPool(3);
        try {
            for (var future : workers.invokeAll(cases)) results.add(future.get());
        } finally {
            workers.shutdownNow();
        }
        long accepted = results.stream().flatMap(result -> result.attempts().stream())
                .filter(attempt -> !attempt.point().isEmpty()).count();
        long total = results.stream().mapToLong(result -> result.attempts().size()).sum();
        if (accepted == 0 || accepted == total) throw new AssertionError("Missing accepted or rejected structure starts");
        var trace = LithoStartProbe.snapshot();
        List<Complete> complete = Boolean.getBoolean("auram.smoke.fullLithoStarts") ? completeChunks(level) : List.of();
        Files.writeString(Path.of("litho-start-parity.json"), new GsonBuilder().setPrettyPrinting().create()
                .toJson(Map.of("results", results, "accepted", accepted, "rejected", total - accepted,
                        "trace", trace, "full_chunks", complete)) + "\n");
    }

    private static List<Complete> completeChunks(ServerLevel level) throws Exception {
        ChunkPos placed = villageStart(level);
        List<Complete> complete = new ArrayList<>();
        for (ChunkPos pos : List.of(new ChunkPos(-12, 84), new ChunkPos(5, 77), placed)) {
            long started = System.nanoTime();
            // Pin the order of cross-chunk decoration while running the
            // ordinary generation stages and converting the target to FULL.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.getChunk(pos.x + dx, pos.z + dz, ChunkStatus.FEATURES, true);
                }
            }
            var chunk = level.getChunk(pos.x, pos.z);
            long elapsed = System.nanoTime() - started;
            var saved = ChunkSerializer.write(level, chunk);
            var hash = MessageDigest.getInstance("SHA-256");
            for (String key : List.of("sections", "block_entities", "structures")) {
                hash.update(TerrainQuerySmoke.canonical(saved.get(key)).getBytes(StandardCharsets.UTF_8));
            }
            complete.add(new Complete(pos.x, pos.z, HexFormat.of().formatHex(hash.digest()), elapsed));
        }
        return complete;
    }

    private static ChunkPos villageStart(ServerLevel level) {
        var set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .get(ResourceLocation.tryBuild("minecraft", "villages"));
        var placement = (RandomSpreadStructurePlacement) set.placement();
        for (int z = -4; z <= 4; z++) {
            for (int x = -4; x <= 4; x++) {
                ChunkPos pos = placement.getPotentialStructureChunk(level.getSeed(), x * placement.spacing(), z * placement.spacing());
                var chunk = level.getChunk(pos.x, pos.z, ChunkStatus.STRUCTURE_STARTS, true);
                for (var entry : set.structures()) {
                    var start = chunk.getStartForStructure(entry.structure().value());
                    if (start != null && start.isValid()) return pos;
                }
            }
        }
        throw new AssertionError("No village start in the seeded fixture area");
    }

    private static Result generate(ServerLevel level, NoiseBasedChunkGenerator generator,
                                   ChunkGeneratorStructureState state, ChunkPos pos) {
        var registry = level.registryAccess();
        var chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, registry.registryOrThrow(Registries.BIOME), null);
        List<Checked> calls = new ArrayList<>();
        ATTEMPTS.set(calls);
        try {
            generator.createStructures(registry, state, level.structureManager(), chunk, level.getServer().getStructureManager());
        } finally {
            ATTEMPTS.remove();
        }

        // The real generate call has completed each context. Its random source
        // is private to this attempt, and the fixture can now read the next value.
        var structures = registry.registryOrThrow(Registries.STRUCTURE);
        List<Attempt> attempts = new ArrayList<>();
        for (Checked call : calls) {
            var random = call.context().random();
            attempts.add(new Attempt(structures.getKey(call.structure()).toString(), call.point(),
                    random.getCount(), random.nextLong()));
        }
        var serialization = StructurePieceSerializationContext.fromLevel(level);
        Map<String, String> starts = new TreeMap<>();
        chunk.getAllStarts().forEach((structure, start) -> starts.put(structures.getKey(structure).toString(),
                TerrainQuerySmoke.canonical(start.createTag(serialization, pos))));
        return new Result(state.getLevelSeed(), pos.x, pos.z, attempts, starts);
    }

    public static void checked(Structure structure, Structure.GenerationContext context, Optional<Structure.GenerationStub> stub) {
        List<Checked> calls = ATTEMPTS.get();
        if (calls != null) calls.add(new Checked(structure, context, stub.map(s -> s.position().toShortString()).orElse("")));
    }

    private record Checked(Structure structure, Structure.GenerationContext context, String point) {
    }

    private record Attempt(String id, String point, int randomCalls, long nextRandom) {
    }

    private record Complete(int x, int z, String fingerprint, long nanos) {
    }

    private record Result(long seed, int x, int z, List<Attempt> attempts, Map<String, String> starts) {
    }
}

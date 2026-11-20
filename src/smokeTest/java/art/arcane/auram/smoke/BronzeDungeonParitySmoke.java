package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BronzeDungeonParitySmoke {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("aether", "the_aether");
    private static final ResourceLocation BRONZE = ResourceLocation.fromNamespaceAndPath("aether", "bronze_dungeon");

    private BronzeDungeonParitySmoke() {}

    public static void run(MinecraftServer server) throws Exception {
        ServerLevel level = Objects.requireNonNull(server.getLevel(ResourceKey.create(Registries.DIMENSION, DIMENSION)));
        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        var structure = Objects.requireNonNull(level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(BRONZE));
        var serialization = StructurePieceSerializationContext.fromLevel(level);
        var timing = ManagementFactory.getThreadMXBean();
        List<Attempt> results = new ArrayList<>();
        List<Long> samples = new ArrayList<>();
        for (long seed : new long[] {104729, 17, 1907}) {
            RandomState random = RandomState.create(generator.generatorSettings().value(),
                    level.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            for (int i = 0; i < 8; i++) {
                generate(level, generator, structure, random, serialization, seed, new ChunkPos(32 + i, 80));
            }
            for (int z = 84; z < 92; z++) {
                for (int x = -8; x < 0; x++) {
                    long start = timing.getCurrentThreadCpuTime();
                    var result = generate(level, generator, structure, random, serialization, seed, new ChunkPos(x, z));
                    samples.add(timing.getCurrentThreadCpuTime() - start);
                    results.add(result);
                }
            }
        }
        long accepted = results.stream().filter(result -> !result.start().isEmpty()).count();
        long pieces = results.stream().mapToLong(result -> result.pieces().size()).sum();
        if (accepted == 0 || accepted == results.size() || pieces == 0) {
            throw new AssertionError("Expected accepted, rejected and built bronze dungeon cases");
        }
        Files.writeString(Path.of("bronze-dungeon-parity.json"), new GsonBuilder().setPrettyPrinting().create().toJson(
                Map.of("attempts", results, "accepted", accepted, "pieces", pieces, "cpu_ns", samples)) + "\n");
    }

    private static Attempt generate(ServerLevel level, NoiseBasedChunkGenerator generator, Structure structure,
                                    RandomState random, StructurePieceSerializationContext serialization,
                                    long seed, ChunkPos pos) {
        var context = new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                random, level.getServer().getStructureManager(), seed, pos, level, biome -> true);
        var stub = structure.findValidGenerationPoint(context);
        List<String> pieces = new ArrayList<>();
        if (stub.isPresent()) {
            // Aether stores adjacency in identity-keyed hash maps. Compare every
            // serialized piece, allowing the pre-existing map iteration order.
            var saved = stub.get().getPiecesBuilder().build().save(serialization);
            for (var piece : (ListTag) saved) pieces.add(TerrainQuerySmoke.canonical(piece));
            pieces.sort(String::compareTo);
        }
        return new Attempt(seed, pos.x, pos.z, stub.map(s -> s.position().toShortString()).orElse(""),
                pieces, context.random().getCount(), context.random().nextLong());
    }

    private record Attempt(long seed, int x, int z, String start, List<String> pieces, int calls, long nextRandom) {}
}

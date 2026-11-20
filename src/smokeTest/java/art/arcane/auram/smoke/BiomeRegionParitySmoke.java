package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import net.minecraft.Util;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class BiomeRegionParitySmoke {
    private BiomeRegionParitySmoke() {}

    public static void run(MinecraftServer server) throws Exception {
        ResourceLocation dimension = new ResourceLocation(System.getProperty("auram.smoke.dimension", "minecraft:the_nether"));
        ServerLevel level = Objects.requireNonNull(server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension)));
        fill(level, 6, 32);
        WorldgenTargets.beginCold();
        List<Long> samples = new ArrayList<>();
        for (int round = 0; round < 3; round++) {
            long start = System.nanoTime();
            fill(level, 48, 84);
            samples.add(System.nanoTime() - start);
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long entries = 0;
        for (var future : fill(level, 48, 84)) {
            ChunkAccess chunk = future.join();
            for (int y = QuartPos.fromBlock(chunk.getMinBuildHeight()); y < QuartPos.fromBlock(chunk.getMaxBuildHeight()); y++) {
                for (int x = 0; x < 4; x++) {
                    for (int z = 0; z < 4; z++) {
                        var biome = chunk.getNoiseBiome(QuartPos.fromBlock(chunk.getPos().getMinBlockX()) + x, y,
                                QuartPos.fromBlock(chunk.getPos().getMinBlockZ()) + z);
                        digest.update(biome.unwrapKey().orElseThrow().location().toString().getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                        entries++;
                    }
                }
            }
        }
        Files.writeString(Path.of("biome-region-parity.json"), new GsonBuilder().setPrettyPrinting().create().toJson(
                Map.of("dimension", dimension.toString(), "entries", entries, "wall_ns", samples,
                        "biome_fingerprint", HexFormat.of().formatHex(digest.digest()))) + "\n");
        WorldgenTargets.write();
        AquiferReuseProbe.write(Path.of("worldgen-stage-cpu.csv"));
    }

    private static List<CompletableFuture<ChunkAccess>> fill(ServerLevel level, int count, int zBase) {
        var generator = level.getChunkSource().getGenerator();
        var state = level.getChunkSource().randomState();
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        List<CompletableFuture<ChunkAccess>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var chunk = new ProtoChunk(new ChunkPos(-8 + i % 8, zBase + i / 8), UpgradeData.EMPTY, level, biomes, null);
            chunk.setStatus(ChunkStatus.STRUCTURE_REFERENCES);
            var region = new WorldGenRegion(level, List.of(chunk), ChunkStatus.BIOMES, 0);
            tasks.add(generator.createBiomes(Util.backgroundExecutor(), state, Blender.empty(),
                    level.structureManager().forWorldGenRegion(region), chunk).thenApply(filled -> {
                        chunk.setStatus(ChunkStatus.BIOMES);
                        return filled;
                    }));
        }
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        return tasks;
    }
}

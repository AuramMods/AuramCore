package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class SurfaceTerrainSmoke {
    public static void run(MinecraftServer server) throws Exception {
        List<Dimension> results = new ArrayList<>();
        for (String name : System.getProperty("auram.smoke.surfaceDimensions",
                "midnight:the_midnight,deeperdarker:otherside,mythicbotany:alfheim,twilightforest:twilight_forest").split(",")) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(name)));
            if (level == null) throw new AssertionError("Missing benchmark dimension " + name);
            results.add(measure(level));
        }
        Files.writeString(Path.of("surface-terrain.json"), new GsonBuilder().setPrettyPrinting().create().toJson(results) + "\n");
    }

    private static Dimension measure(ServerLevel level) throws Exception {
        String name = level.dimension().location().toString();
        LogUtils.getLogger().info("[AURAM SURFACE] Warming {}, seed {}", name, level.getSeed());
        for (int x = 14; x < 17; x++) {
            for (int z = -18; z < -16; z++) level.getChunk(x, z, ChunkStatus.SURFACE, true);
        }
        List<ChunkCheck> surface = new ArrayList<>();
        SurfaceWorkProbe.start(level.getChunkSource().randomState());
        SurfaceWorkProbe.Result work;
        try {
            for (int x = -34; x < -28; x++) {
                for (int z = 48; z < 52; z++) {
                    var chunk = level.getChunk(x, z, ChunkStatus.SURFACE, true);
                    surface.add(check(x, z, ChunkSerializer.write(level, chunk), 0));
                }
            }
        } finally {
            work = SurfaceWorkProbe.stop();
        }
        LogUtils.getLogger().info("[AURAM SURFACE] {} surface builds for {}", work.builds(), name);
        List<ChunkCheck> full = new ArrayList<>();
        for (var pos : List.of(new ChunkPos(-34, 48), new ChunkPos(-30, 50))) {
            long start = System.nanoTime();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.getChunk(pos.x + dx, pos.z + dz, ChunkStatus.FEATURES, true);
                }
            }
            var chunk = level.getChunk(pos.x, pos.z);
            long elapsed = System.nanoTime() - start;
            full.add(check(pos.x, pos.z, ChunkSerializer.write(level, chunk), elapsed));
        }
        return new Dimension(name, level.getSeed(), work, surface, full);
    }

    private static ChunkCheck check(int x, int z, CompoundTag saved, long nanos) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        for (String key : List.of("sections", "block_entities", "structures")) {
            digest.update(TerrainQuerySmoke.canonical(saved.get(key)).getBytes(StandardCharsets.UTF_8));
        }
        return new ChunkCheck(x, z, saved.getString("Status"), HexFormat.of().formatHex(digest.digest()), nanos);
    }

    private record ChunkCheck(int x, int z, String status, String sha256, long nanos) {
    }

    private record Dimension(String name, long seed, SurfaceWorkProbe.Result work, List<ChunkCheck> surface, List<ChunkCheck> full) {
    }
}

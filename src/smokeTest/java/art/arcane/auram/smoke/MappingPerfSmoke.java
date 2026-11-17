package art.arcane.auram.smoke;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.IdentityHashMap;

/** Warmed, identical terrain-query workload for independently booted A/B jars. */
public final class MappingPerfSmoke {
    public static void run(MinecraftServer server) throws Exception {
        var results = new ArrayList<String>();
        for (var level : server.getAllLevels()) {
            if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) continue;
            var times = new ArrayList<Double>();
            var digest = MessageDigest.getInstance("SHA-256");
            var encoded = new IdentityHashMap<BlockState, byte[]>();
            NoiseColumn[] columns = new NoiseColumn[16];
            for (int batch = -8; batch < 25; batch++) {
                long start = System.nanoTime();
                for (int i = 0; i < columns.length; i++) {
                    int x = (batch + 8) * 31 + i * 17 - 700;
                    int z = (batch + 8) * -19 + i * 13 + 357;
                    columns[i] = generator.getBaseColumn(x, z, level, level.getChunkSource().randomState());
                }
                if (batch < 0) continue;
                times.add((System.nanoTime() - start) / 1e6);
                // Fingerprinting is outside the measured interval.
                for (var column : columns) {
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        digest.update(encoded.computeIfAbsent(column.getBlock(y), s -> s.toString().getBytes(StandardCharsets.UTF_8)));
                    }
                }
            }
            var sorted = new ArrayList<>(times);
            Collections.sort(sorted);
            String result = String.format(java.util.Locale.ROOT,
                    "{\"dimension\":\"%s\",\"warmup_batches\":8,\"measured_batches\":25,\"columns_per_batch\":16,\"median_batch_ms\":%.6f,\"p95_batch_ms\":%.6f,\"fingerprint\":\"%s\",\"batch_ms\":%s}",
                    level.dimension().location(), sorted.get(12), sorted.get(23), HexFormat.of().formatHex(digest.digest()), times);
            results.add(result);
            com.mojang.logging.LogUtils.getLogger().info("[AURAM MAPPING PERF] {}", result);
        }
        Files.writeString(Path.of("mappingperf.json"), "{\"results\":[" + String.join(",", results) + "]}\n");
    }
}

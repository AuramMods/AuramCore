package art.arcane.auram.smoke;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/** Measures cache-hit cost against the actual Better Caves table implementation. */
final class BetterCavesLookupSmoke {
    private static final String CLASS_NAME =
            "com.yungnickyoung.minecraft.bettercaves.worldgen.liquidregion.LiquidRegions";

    static void run() throws Throwable {
        Class<?> type = Class.forName(CLASS_NAME);
        Class<?> settingsType = Class.forName(CLASS_NAME + "$Settings");
        Class<?> dataType = Class.forName(CLASS_NAME + "$CacheData");
        Object settings = settingsType.getField("DEFAULT").get(null);
        Object liquids = type.getConstructor(RandomSource.class, settingsType)
                .newInstance(RandomSource.create(104729), settings);
        var lookup = MethodHandles.publicLookup().findVirtual(type, "getOrCreateLiquidBlocksForChunk",
                MethodType.methodType(dataType, ChunkPos.class)).bindTo(liquids)
                .asType(MethodType.methodType(Object.class, ChunkPos.class));
        var states = dataType.getMethod("liquidBlocks");
        var altitude = dataType.getMethod("liquidAltitude");
        var digest = MessageDigest.getInstance("SHA-256");
        var positions = new ChunkPos[16];
        var tables = new Object[positions.length];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = new ChunkPos(i - 8, 5 - i);
            tables[i] = lookup.invokeExact(positions[i]);
            digest.update(altitude.invoke(tables[i]).toString().getBytes(StandardCharsets.UTF_8));
            for (BlockState[] column : (BlockState[][]) states.invoke(tables[i])) {
                for (BlockState block : column) digest.update(String.valueOf(block).getBytes(StandardCharsets.UTF_8));
            }
        }

        var allocations = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().getId();
        double[] durations = new double[19];
        long[] bytes = new long[durations.length];
        int calls = 250_000;
        for (int batch = -10; batch < durations.length; batch++) {
            long allocated = allocations.getThreadAllocatedBytes(thread);
            long start = System.nanoTime();
            for (int i = 0; i < calls; i++) {
                int index = i & 15;
                if (lookup.invokeExact(positions[index]) != tables[index]) {
                    throw new AssertionError("Better Caves did not return its cached table");
                }
            }
            if (batch >= 0) {
                durations[batch] = (System.nanoTime() - start) / (double) calls;
                bytes[batch] = allocations.getThreadAllocatedBytes(thread) - allocated;
            }
        }
        Arrays.sort(durations);
        Arrays.sort(bytes);
        Files.writeString(Path.of("bettercaves-lookup.json"), String.format(Locale.ROOT,
                "{\"fingerprint\":\"%s\",\"median_hit_ns\":%.3f,\"p95_hit_ns\":%.3f,\"median_allocated_bytes_per_lookup\":%.3f}%n",
                HexFormat.of().formatHex(digest.digest()), durations[9], durations[17], bytes[9] / (double) calls));
    }
}

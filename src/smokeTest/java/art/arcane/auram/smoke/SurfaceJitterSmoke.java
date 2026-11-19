package art.arcane.auram.smoke;

import art.arcane.auram.util.DitheringSurfaceAccess;
import art.arcane.auram.util.SurfaceBiomeSampler;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.fml.ModList;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.Function;

public final class SurfaceJitterSmoke {
    public static void run(MinecraftServer server) throws Exception {
        Holder<Biome> biome = server.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        Map<String, Measurements> measurements = new LinkedHashMap<>();
        measurements.put("vanilla", verifyAndMeasure(biome, false));
        if (ModList.get().isLoaded("puffish_biome_dither")) measurements.put("puffish", verifyAndMeasure(biome, true));

        // Run the real constructor hook as well as direct numerical checks.
        var chunk = server.overworld().getChunk(-7, 12);
        Files.writeString(Path.of("surface-jitter.json"), new GsonBuilder().setPrettyPrinting().create()
                .toJson(Map.of("modes", measurements, "chunk_status", chunk.getStatus().toString())) + "\n");
    }

    private static Measurements verifyAndMeasure(Holder<Biome> biome, boolean dither) throws Exception {
        var tasks = new ArrayList<Callable<Integer>>();
        for (long seed : new long[]{0L, 104729L, Long.MIN_VALUE, Long.MAX_VALUE, -319217L}) {
            tasks.add(() -> verify(biome, seed, dither));
        }
        int compared = 0;
        var executor = Executors.newFixedThreadPool(3);
        try {
            for (var task : executor.invokeAll(tasks)) compared += task.get();
        } finally {
            executor.shutdownNow();
        }

        var thread = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        List<Sample> reference = new ArrayList<>(), candidate = new ArrayList<>();
        for (int i = -6; i < 16; i++) {
            Sample a = measure(thread, biome, dither, (i & 1) == 0);
            Sample b = measure(thread, biome, dither, (i & 1) != 0);
            if (a.checksum() != b.checksum()) throw new AssertionError("Timed surface winners changed");
            if (i >= 0) {
                reference.add((i & 1) == 0 ? b : a);
                candidate.add((i & 1) == 0 ? a : b);
            }
        }
        return new Measurements(compared, reference, candidate);
    }

    private static int verify(Holder<Biome> biome, long seed, boolean dither) throws Exception {
        Sink before = new Sink(biome), after = new Sink(biome);
        var original = getter(seed, before, dither, false);
        var cached = getter(seed, after, dither, true);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int count = 0;
        for (int origin : new int[]{-33, -16, -1, 0, 17, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            for (int dx = -1; dx <= 16; dx++) {
                for (int dz = -1; dz <= 16; dz++) {
                    for (int y = 1060; y >= -96; y -= 3) {
                        pos.set(origin + dx, y, -origin + dz);
                        if (original.apply(pos) != cached.apply(pos) || !before.sameCorner(after)) {
                            throw new AssertionError("Surface biome corner differs at " + pos + " seed " + seed);
                        }
                        count++;
                    }
                }
            }
        }
        if (before.calls != count || after.calls != count) throw new AssertionError("Final biome lookup count differs");
        return count;
    }

    private static Sample measure(com.sun.management.ThreadMXBean thread, Holder<Biome> biome, boolean dither, boolean patched) throws Exception {
        Sink sink = new Sink(biome);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long threadId = Thread.currentThread().getId();
        long allocated = thread.getThreadAllocatedBytes(threadId);
        long started = thread.getCurrentThreadCpuTime();
        for (int chunk = 0; chunk < 6; chunk++) {
            long seed = 104729L + chunk;
            Function<BlockPos, Holder<Biome>> get = getter(seed, sink, dither, patched);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 152; y >= -64; y--) get.apply(pos.set(x - 64 + chunk * 16, y, z + 32));
                }
            }
        }
        return new Sample(thread.getCurrentThreadCpuTime() - started, thread.getThreadAllocatedBytes(threadId) - allocated,
                sink.checksum);
    }

    private static Function<BlockPos, Holder<Biome>> getter(long seed, Sink sink, boolean dither, boolean patched) throws Exception {
        if (!dither) return patched ? new SurfaceBiomeSampler(seed, sink) : new BiomeManager(sink, seed)::getBiome;
        var type = Class.forName("net.puffish.biomedithermod.DitheringBiomeAccess");
        var manager = (BiomeManager) type.getConstructor(BiomeManager.NoiseBiomeSource.class, long.class).newInstance(sink, seed);
        return patched ? ((DitheringSurfaceAccess) manager).auram$surfaceGetter(new SurfaceBiomeSampler(seed, sink))
                : manager::getBiome;
    }

    private static final class Sink implements BiomeManager.NoiseBiomeSource {
        private final Holder<Biome> biome;
        private int x, y, z, calls;
        private long checksum;

        Sink(Holder<Biome> biome) {
            this.biome = biome;
        }

        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            calls++;
            checksum = checksum * 31 + x * 1193L + y * 1507L + z * 1733L;
            return biome;
        }

        boolean sameCorner(Sink other) {
            return x == other.x && y == other.y && z == other.z;
        }
    }

    private record Measurements(int cornerWinners, List<Sample> reference, List<Sample> candidate) {
    }

    private record Sample(long cpuNanos, long allocatedBytes, long checksum) {
    }
}

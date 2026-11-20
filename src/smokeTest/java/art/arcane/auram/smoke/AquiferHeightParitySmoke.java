package art.arcane.auram.smoke;

import art.arcane.auram.mixin.minecraft.NoiseGeneratorAccess;
import art.arcane.auram.smoke.mixin.BlenderFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.blending.BlendingData;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class AquiferHeightParitySmoke {
    private static final DensityFunctions.BeardifierOrMarker ZERO = new DensityFunctions.BeardifierOrMarker() {
        @Override public double compute(DensityFunction.FunctionContext context) { return 0; }
        @Override public double minValue() { return 0; }
        @Override public double maxValue() { return 0; }
    };

    private AquiferHeightParitySmoke() {}

    public static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        var settings = generator.generatorSettings().value();
        if (!settings.isAquifersEnabled()) throw new AssertionError("Aquifer fixture requires enabled aquifers");
        var fluids = ((NoiseGeneratorAccess) generator).auram$fluidPicker().get();
        var noiseSettings = settings.noiseSettings().clampToHeightAccessor(level);
        var clipped = noiseSettings.clampToHeightAccessor(
                LevelHeightAccessor.create(noiseSettings.minY(), noiseSettings.height() / 2));
        if (clipped.equals(noiseSettings)) throw new AssertionError("Expected a second effective height range");

        var digest = MessageDigest.getInstance("SHA-256");
        var bytes = ByteBuffer.allocate(Integer.BYTES);
        int queries = 0;
        for (long seed : new long[] {104729, 17, 1907}) {
            var random = RandomState.create(settings, level.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            for (var shape : new NoiseSettings[] {noiseSettings, clipped, noiseSettings}) {
                var scope = AquiferReuseProbe.enter(AquiferReuseProbe.Stage.NOISE, generator, random);
                try {
                    long lookups = AquiferReuseProbe.sharedLookups(scope);
                    int cells = 16 / shape.getCellWidth();
                    for (int x = -2; x <= 2; x++) {
                        for (int z = -2; z <= 2; z++) {
                            var sampler = new NoiseChunk(cells, random, x * 16, z * 16, shape,
                                    ZERO, settings, fluids, Blender.empty());
                            for (int dx = -3; dx <= 3; dx++) {
                                for (int dz = -3; dz <= 3; dz++) {
                                    int height = sampler.preliminarySurfaceLevel(x * 16 + dx * 4, z * 16 + dz * 4);
                                    digest.update(bytes.putInt(0, height).array());
                                    queries++;
                                }
                            }
                        }
                    }
                    if (shape == clipped && AquiferReuseProbe.sharedLookups(scope) != lookups) {
                        throw new AssertionError("The clipped height range accessed the full-height cache");
                    }
                } finally {
                    AquiferReuseProbe.leave(scope);
                }
            }
        }
        Files.writeString(Path.of("aquifer-parity.txt"), queries + " " + HexFormat.of().formatHex(digest.digest()) + "\n");
        var noises = level.registryAccess().lookupOrThrow(Registries.NOISE);
        var unsupported = withUnknownDensity(settings);
        var blended = BlenderFactory.auramSmoke$create(blendingHeights(), new Long2ObjectOpenHashMap<>());
        var blendSeed = RandomState.create(settings, noises, 104729);
        // Bind the unblended cache first, then use the same seed and column in a blended sampler.
        exerciseFallback(generator, blendSeed, noiseSettings, settings, fluids, Blender.empty(), false, digest);
        exerciseFallback(generator, blendSeed, noiseSettings, settings, fluids, blended, true, digest);
        exerciseFallback(generator, RandomState.create(unsupported, noises, 104729), noiseSettings,
                unsupported, fluids, Blender.empty(), true, digest);
        Files.writeString(Path.of("aquifer-fallback-parity.txt"), HexFormat.of().formatHex(digest.digest()) + "\n");
        AquiferReuseProbe.write(Path.of("worldgen-stage-cpu.csv"));
    }

    private static void exerciseFallback(NoiseBasedChunkGenerator generator, RandomState random, NoiseSettings shape,
                                         NoiseGeneratorSettings settings, Aquifer.FluidPicker fluids, Blender blender,
                                         boolean expectFallback, MessageDigest digest) {
        var scope = AquiferReuseProbe.enter(AquiferReuseProbe.Stage.NOISE, generator, random);
        try {
            long lookups = AquiferReuseProbe.sharedLookups(scope);
            var bytes = ByteBuffer.allocate(Integer.BYTES);
            for (int chunk = 0; chunk < 2; chunk++) {
                var sampler = new NoiseChunk(16 / shape.getCellWidth(), random, chunk * 16, 0, shape,
                        ZERO, settings, fluids, blender);
                for (int x = 0; x < 24; x += 4) {
                    digest.update(bytes.putInt(0, sampler.preliminarySurfaceLevel(x, 0)).array());
                }
            }
            if (expectFallback && AquiferReuseProbe.sharedLookups(scope) != lookups) {
                throw new AssertionError("A blended or unsupported sampler accessed the shared cache");
            }
        } finally {
            AquiferReuseProbe.leave(scope);
        }
    }

    private static Long2ObjectOpenHashMap<BlendingData> blendingHeights() {
        var data = new JsonObject();
        data.addProperty("min_section", 0);
        data.addProperty("max_section", 16);
        var heights = new JsonArray();
        for (int column = 0; column < 16; column++) heights.add(72.0);
        data.add("heights", heights);
        var oldChunk = BlendingData.CODEC.parse(JsonOps.INSTANCE, data)
                .getOrThrow(false, message -> { throw new AssertionError(message); });
        var chunks = new Long2ObjectOpenHashMap<BlendingData>();
        chunks.put(ChunkPos.asLong(0, 0), oldChunk);
        return chunks;
    }

    private static NoiseGeneratorSettings withUnknownDensity(NoiseGeneratorSettings settings) {
        var zero = DensityFunctions.constant(0);
        var unknown = new DensityFunction.SimpleFunction() {
            @Override public double compute(DensityFunction.FunctionContext context) { return context.blockY() / 128.0; }
            @Override public double minValue() { return -Double.MAX_VALUE; }
            @Override public double maxValue() { return Double.MAX_VALUE; }
            @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() {
                throw new UnsupportedOperationException("The fixture density is not serialized");
            }
        };
        var router = new NoiseRouter(zero, zero, zero, zero, zero, zero, zero, zero, zero, zero,
                unknown, zero, zero, zero, zero);
        return new NoiseGeneratorSettings(settings.noiseSettings(), settings.defaultBlock(), settings.defaultFluid(),
                router, settings.surfaceRule(), settings.spawnTarget(), settings.seaLevel(),
                settings.disableMobGeneration(), settings.isAquifersEnabled(), settings.oreVeinsEnabled(),
                settings.useLegacyRandomSource());
    }
}

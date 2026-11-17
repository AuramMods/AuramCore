package art.arcane.auram.smoke;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;

/** Exercises the installed mod's real generatePieces entry point and both column call sites. */
public final class WroughtColumnsSmoke {
    public static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var generator = level.getChunkSource().getGenerator();
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.tryParse("mowziesmobs:wrought_chamber"));
        if (structure == null) throw new AssertionError("Missing Wroughtnaut fixture");
        var method = structure.getClass().getMethod("generatePieces", StructurePiecesBuilder.class, Structure.GenerationContext.class);
        var digest = MessageDigest.getInstance("SHA-256");
        var times = new ArrayList<Double>();
        int pieces = 0;
        TerrainQueryProbe.start();
        try {
            for (int i = 0; i < 24; i++) {
                TerrainQueryProbe.nextAttempt();
                var context = new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                        level.getChunkSource().randomState(), server.getStructureManager(), level.getSeed(),
                        new ChunkPos(80 + i * 3, 80 - i * 2), level, biome -> true);
                var builder = new StructurePiecesBuilder();
                long start = System.nanoTime();
                method.invoke(structure, builder, context);
                times.add((System.nanoTime() - start) / 1e6);
                var container = builder.build();
                pieces += container.pieces().size();
                digest.update(container.save(StructurePieceSerializationContext.fromLevel(level)).toString().getBytes(StandardCharsets.UTF_8));
                digest.update(Long.toString(context.random().nextLong()).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable failure) {
            TerrainQueryProbe.stop();
            throw failure;
        }
        var trace = TerrainQueryProbe.stop();
        String result = "{\"attempts\":24,\"pieces\":" + pieces + ",\"fingerprint\":\"" + HexFormat.of().formatHex(digest.digest())
                + "\",\"trace\":" + trace.json() + ",\"attempt_ms\":" + times + "}";
        Files.writeString(Path.of("wrought-columns.json"), result + "\n");
        com.mojang.logging.LogUtils.getLogger().info("[AURAM WROUGHT] {}", result);
    }
}

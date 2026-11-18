package art.arcane.auram.smoke;

import art.arcane.auram.util.BiomeFillBounds;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real dispatcher commands, decoded biome packets and warmed command benchmarks. */
public final class BiomeFillSmoke {
    public static void run(MinecraftServer server, boolean reference) throws Exception {
        List<Map<String, Object>> semantics = new ArrayList<>(), work = new ArrayList<>();
        for (ServerLevel level : List.of(server.overworld(), server.getLevel(Level.NETHER), server.getLevel(Level.END))) {
            List<LevelChunk> chunks = new ArrayList<>();
            for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) chunks.add(level.getChunk(x, z));
            Sink sink = new Sink();
            CommandSourceStack source = source(server, level, sink);
            var limit = level.getGameRules().getRule(GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT);
            int originalLimit = limit.get();
            limit.set(1_000_000, server);
            int min = level.getMinBuildHeight(), max = level.getMaxBuildHeight() - 1;
            int[][] corners = {{-1, min, -1, 16, min + 16, 16}, {15, 0, 15, 16, 0, 16},
                    {16, 31, 16, -1, -17, -1}, {-8, 79, -8, 8, 80, 8},
                    {0, min, 0, 0, max, 0}, {1, min - 1, 1, 8, min + 4, 8},
                    {1, max - 4, 1, 8, max + 20, 8}, {8, 64, 8, 8, 64, 8}, {0, -1, 0, 3, 0, 3},
                    {16, 16, 16, -1, 15, -1}, {0, min + 1, 0, 8, min + 44, 8}};
            for (int[] box : corners) for (String suffix : new String[]{"", " replace minecraft:snowy_slopes",
                    " replace minecraft:plains", " replace #minecraft:is_overworld"}) {
                seed(level, chunks);
                String command = "fillbiome " + coordinates(box) + " minecraft:snowy_taiga" + suffix;
                capture(server, level, source, sink, chunks, command, semantics, work, reference, box);
            }
            seed(level, chunks);
            limit.set(1, server);
            capture(server, level, source, sink, chunks, "fillbiome 0 64 0 8 80 8 minecraft:plains", semantics, work, reference, null);
            limit.set(1_000_000, server);
            capture(server, level, source, sink, chunks, "fillbiome 160000 64 160000 160008 80 160008 minecraft:plains",
                    semantics, work, reference, null);
            limit.set(originalLimit, server);
        }
        Files.writeString(Path.of("biome-semantics.json"), json(semantics));
        Files.writeString(Path.of("biome-work.json"), json(work));
        if (!reference) scopeChecks(server.overworld());
        Files.writeString(Path.of("biome-benchmark.json"), json(benchmarks(server)));
    }

    private static void capture(MinecraftServer server, ServerLevel level, CommandSourceStack source, Sink sink,
                                List<LevelChunk> chunks, String command, List<Map<String, Object>> semantics,
                                List<Map<String, Object>> work, boolean reference, int[] box) throws Exception {
        sink.messages.clear();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimension", level.dimension().location().toString()); result.put("command", command);
        BiomeFillProbe.start();
        boolean success;
        try {
            result.put("result", server.getCommands().getDispatcher().execute(command, source));
            success = true;
        } catch (CommandSyntaxException error) {
            result.put("error", error.getRawMessage().getString()); success = false;
        }
        BiomeFillProbe.Stats stats = BiomeFillProbe.stop();
        result.put("messages", List.copyOf(sink.messages)); result.put("refreshes", stats.refreshes);
        result.put("biomes", BiomeFillProbe.digest(level, chunks));
        result.put("dirty", chunks.stream().map(LevelChunk::isUnsaved).toList());
        if (success) {
            int count = ((List<?>) stats.refreshes.get(0).get("chunks")).size();
            int sections = reference ? level.getSectionsCount() : Math.max(0,
                    Math.min(level.getMaxSection(), (Math.max(box[1], box[4]) >> 4) + 1)
                            - Math.max(level.getMinSection(), Math.min(box[1], box[4]) >> 4));
            check(stats.reads == (long) count * sections * 64, "exact palette work matches bounded/original range: " + command);
        } else check(stats.reads == 0 && stats.refreshes.isEmpty(), "failed command does not rewrite palettes");
        semantics.add(result);
        work.add(Map.of("dimension", level.dimension().location().toString(), "command", command, "resolver_calls", stats.reads));
    }

    private static List<Map<String, Object>> benchmarks(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        var limit = level.getGameRules().getRule(GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT);
        int oldLimit = limit.get(); limit.set(1_000_000, server);
        CommandSourceStack source = source(server, level, CommandSource.NULL).withSuppressedOutput();
        var dispatcher = server.getCommands().getDispatcher();
        List<Map<String, Object>> results = new ArrayList<>();
        List<LevelChunk> fixture = new ArrayList<>();
        for (int z = 0; z <= 1; z++) for (int x = 0; x <= 1; x++) fixture.add(level.getChunk(x, z));
        for (String workload : new String[]{"partial_miss", "partial_toggle", "full_height"}) {
            seed(level, fixture);
            String from = workload.equals("full_height") ? "8 -64 8 16 319 16" : "8 60 8 16 104 16";
            String a = "fillbiome " + from + " minecraft:snowy_taiga replace " + (workload.equals("partial_miss") ? "minecraft:swamp" : "minecraft:plains");
            String b = "fillbiome " + from + " minecraft:plains replace minecraft:snowy_taiga";
            if (workload.equals("partial_miss")) b = a;
            var parsedA = dispatcher.parse(a, source); var parsedB = dispatcher.parse(b, source);
            double[] elapsed = new double[25]; long checksum = 0;
            for (int batch = -5; batch < 25; batch++) {
                long start = System.nanoTime();
                for (int call = 0; call < 400; call++) checksum += dispatcher.execute(call % 2 == 0 ? parsedA : parsedB);
                if (batch >= 0) elapsed[batch] = (System.nanoTime() - start) / 1e6;
            }
            double[] sorted = elapsed.clone(); Arrays.sort(sorted);
            results.add(Map.of("workload", workload, "calls_per_batch", 400, "warmup_batches", 5,
                    "measured_batches", 25, "median_ms", sorted[12], "p95_ms", sorted[23], "batch_ms", elapsed, "checksum", checksum));
        }
        limit.set(oldLimit, server);
        return results;
    }

    private static void scopeChecks(ServerLevel level) {
        LevelChunk a = level.getChunk(0, 0), b = level.getChunk(1, 0), c = level.getChunk(-1, 0);
        var sampler = level.getChunkSource().randomState().sampler();
        Holder<Biome> plains = level.registryAccess().registryOrThrow(Registries.BIOME)
                .getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        long[] outerCalls = {0}, foreignCalls = {0}; boolean[] first = {true};
        BiomeResolver[] outer = new BiomeResolver[1];
        BiomeResolver failure = (x, y, z, s) -> { throw new ExpectedFailure(); };
        outer[0] = (x, y, z, s) -> {
            outerCalls[0]++;
            if (first[0]) {
                first[0] = false;
                try { BiomeFillBounds.fill(b, failure, sampler, 64, 64); throw new AssertionError("missing injected failure"); }
                catch (ExpectedFailure expected) { }
                check(BiomeFillBounds.minimum(outer[0], -4) == 0, "nested exception restores outer bounds");
                c.fillBiomesFromNoise((fx, fy, fz, fs) -> { foreignCalls[0]++; return plains; }, sampler);
                check(CompletableFuture.supplyAsync(() -> BiomeFillBounds.minimum(outer[0], -4)).join() == -4,
                        "scope does not leak to another worker");
            }
            return plains;
        };
        BiomeFillBounds.fill(a, outer[0], sampler, 0, 31);
        check(outerCalls[0] == 128, "outer bounded operation survives nested failure");
        check(foreignCalls[0] == level.getSectionsCount() * 64L, "different resolver retains full generation range");
        check(BiomeFillBounds.minimum(outer[0], -4) == -4, "normal exit clears scope");
        try { BiomeFillBounds.fill(a, failure, sampler, 0, 0); throw new AssertionError("missing outer failure"); }
        catch (ExpectedFailure expected) { }
        check(BiomeFillBounds.maximum(failure, 20) == 20, "exception exit clears scope");
    }

    private static void seed(ServerLevel level, List<LevelChunk> chunks) {
        var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        List<Holder.Reference<Biome>> palette = List.of("plains", "snowy_slopes", "snowy_taiga", "desert", "savanna", "badlands").stream()
                .map(id -> registry.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.BIOME, ResourceLocation.tryParse("minecraft:" + id)))).toList();
        for (LevelChunk chunk : chunks) {
            chunk.fillBiomesFromNoise((x, y, z, sampler) -> palette.get(Math.floorMod(x * 37 + y * 11 + z * 19, palette.size())),
                    level.getChunkSource().randomState().sampler());
            chunk.setUnsaved(false);
        }
    }

    private static CommandSourceStack source(MinecraftServer server, ServerLevel level, CommandSource sink) {
        return new CommandSourceStack(sink, Vec3.ZERO, Vec2.ZERO, level, 4, "BiomeProbe", Component.literal("BiomeProbe"), server, null);
    }

    private static String coordinates(int[] box) {
        return String.join(" ", Arrays.stream(box).mapToObj(Integer::toString).toList());
    }

    private static final class Sink implements CommandSource {
        final List<String> messages = new ArrayList<>();
        public void sendSystemMessage(Component component) { messages.add(Component.Serializer.toJson(component)); }
        public boolean acceptsSuccess() { return true; }
        public boolean acceptsFailure() { return true; }
        public boolean shouldInformAdmins() { return false; }
    }

    private static String json(Object value) { return new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n"; }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class ExpectedFailure extends RuntimeException { }
}

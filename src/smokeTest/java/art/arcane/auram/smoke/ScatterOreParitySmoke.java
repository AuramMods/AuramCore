package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ScatterOreParitySmoke {
    private static final ResourceLocation FEATURE = ResourceLocation.fromNamespaceAndPath("dreamtinker", "shadow_silver_ore");

    private ScatterOreParitySmoke() {}

    public static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var registries = level.registryAccess();
        var generator = level.getChunkSource().getGenerator();
        var feature = Objects.requireNonNull(registries.registryOrThrow(Registries.CONFIGURED_FEATURE).get(FEATURE));
        var blocks = registries.registryOrThrow(Registries.BLOCK);
        BlockState silver = silver(blocks, "stone");
        BlockState deepSilver = silver(blocks, "deepslate");
        var existing = Objects.requireNonNull(blocks.get(FEATURE)).defaultBlockState();
        List<Result> results = new ArrayList<>();
        List<Long> samples = new ArrayList<>();
        for (int index = -24; index < 120; index++) {
            var origin = new BlockPos(-32, 25, 18);
            var fixture = new OreBox(blocks, silver, deepSilver, existing, index);
            var world = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
                    new Class<?>[] { WorldGenLevel.class }, (proxy, method, args) -> {
                        // Method names are SRG on the production-mapped server.
                        if (method.getReturnType() == BlockState.class && args != null && args.length == 1
                                && args[0] instanceof BlockPos pos) return fixture.read(pos);
                        if (method.getReturnType() == boolean.class && args != null && args.length == 3
                                && args[0] instanceof BlockPos pos && args[1] instanceof BlockState state) {
                            return fixture.write(pos, state);
                        }
                        if (method.getReturnType() == boolean.class && args != null && args.length == 1
                                && args[0] instanceof BlockPos) return true;
                        throw new AssertionError("Unexpected ore fixture world call " + method);
                    });
            long seed = 104729L + index;
            var random = new WorldgenRandom(new LegacyRandomSource(seed));
            long cpu = WorldgenTargets.cpu();
            boolean placed = feature.place(world, generator, random, origin);
            long elapsed = WorldgenTargets.cpu() - cpu;
            if (index >= 0) {
                samples.add(elapsed);
                results.add(new Result(seed, placed, random.getCount(), random.nextLong(), fixture.writes));
            }
        }
        long writes = results.stream().mapToLong(result -> result.writes().size()).sum();
        if (writes == 0 || results.stream().allMatch(Result::placed)) {
            throw new AssertionError("Expected ore placements and no-op attempts");
        }
        Files.writeString(Path.of("scatter-ore-parity.json"), new GsonBuilder().setPrettyPrinting().create().toJson(
                Map.of("attempts", results, "writes", writes, "cpu_ns", samples)) + "\n");
        WorldgenTargets.write();
    }

    private static BlockState silver(net.minecraft.core.Registry<Block> blocks, String stone) {
        TagKey<Block> silver = tag("ores/silver");
        TagKey<Block> base = tag("ores_in_ground/" + stone);
        return blocks.stream().map(Block::defaultBlockState).filter(state -> state.is(silver) && state.is(base))
                .findFirst().orElseThrow(() -> new AssertionError("Missing tagged silver in " + stone));
    }

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("forge", path));
    }

    private record Result(long seed, boolean placed, int rngCalls, long nextRandom, List<String> writes) {}

    private static final class OreBox {
        private final net.minecraft.core.Registry<Block> blocks;
        private final BlockState silver;
        private final BlockState deepSilver;
        private final BlockState existing;
        private final int variant;
        private final Map<Long, BlockState> changes = new HashMap<>();
        private final List<String> writes = new ArrayList<>();

        OreBox(net.minecraft.core.Registry<Block> blocks, BlockState silver, BlockState deepSilver,
               BlockState existing, int variant) {
            this.blocks = blocks;
            this.silver = silver;
            this.deepSilver = deepSilver;
            this.existing = existing;
            this.variant = variant;
        }

        BlockState read(BlockPos pos) {
            BlockState changed = changes.get(pos.asLong());
            if (changed != null) return changed;
            if (variant % 4 == 0) return Blocks.STONE.defaultBlockState();
            int band = Math.floorMod(pos.getX() + 3 * pos.getY() + 5 * pos.getZ() + variant, 11);
            if (band < 4) return silver;
            if (band < 7) return deepSilver;
            if (band == 7) return existing;
            return Blocks.STONE.defaultBlockState();
        }

        boolean write(BlockPos pos, BlockState state) {
            changes.put(pos.asLong(), state);
            writes.add(pos.asLong() + "=" + blocks.getKey(state.getBlock()));
            return true;
        }
    }
}

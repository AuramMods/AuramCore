package art.arcane.auram.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Exercise the installed no-rule path and check dispatch order with two observable rules. */
final class WeatheringGrowthSmoke {
    private static final String PACKAGE = "com.ordana.immersive_weathering.data.block_growths.";

    @SuppressWarnings("unchecked")
    static void run(ServerLevel level) throws Throwable {
        Class<?> handler = Class.forName(PACKAGE + "BlockGrowthHandler");
        Class<?> tickSource = Class.forName(PACKAGE + "TickSource");
        Class<?> rule = Class.forName(PACKAGE + "growths.IBlockGrowth");
        Object source = tickSource.getField("BLOCK_TICK").get(null);
        var tick = MethodHandles.publicLookup()
                .findStatic(handler, "tickBlock", MethodType.methodType(void.class, tickSource,
                        BlockState.class, ServerLevel.class, BlockPos.class))
                .asType(MethodType.methodType(void.class, Object.class, BlockState.class, ServerLevel.class, BlockPos.class));
        var universalField = handler.getDeclaredField("UNIVERSAL_GROWTHS");
        universalField.setAccessible(true);
        var blockField = handler.getDeclaredField("GROWTH_FOR_BLOCK");
        blockField.setAccessible(true);
        var universal = (Map<Object, Set<Object>>) universalField.get(null);
        var byBlock = (Map<Object, Map<Object, Set<Object>>>) blockField.get(null);
        BlockState state = Blocks.BEDROCK.defaultBlockState();
        BlockPos pos = level.getSharedSpawnPos();
        require(universal.getOrDefault(source, Set.of()).isEmpty(), "installed block ticks have no universal rule");
        require(byBlock.getOrDefault(source, Map.of()).getOrDefault(state.getBlock(), Set.of()).isEmpty(),
                "bedrock has no installed growth rule");

        double[] elapsed = new double[19];
        for (int batch = -10; batch < elapsed.length; batch++) {
            long start = System.nanoTime();
            for (int i = 0; i < 250_000; i++) tick.invokeExact(source, state, level, pos);
            if (batch >= 0) elapsed[batch] = (System.nanoTime() - start) / 250_000.0;
        }
        Arrays.sort(elapsed);

        var order = new ArrayList<String>();
        var randomDraws = new ArrayList<Integer>();
        var suppliers = new ArrayList<Object>();
        class Rules {
            Object add(String label) {
                return Proxy.newProxyInstance(rule.getClassLoader(), new Class<?>[]{rule}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "tryGrowing" -> {
                            order.add(label);
                            randomDraws.add(level.getRandom().nextInt());
                            suppliers.add(args[3]);
                            require(((Supplier<?>) args[3]).get() == level.getBiome(pos), "original biome supplier");
                            yield null;
                        }
                        default -> throw new AssertionError(method);
                    };
                });
            }
        }
        var factory = new Rules();
        var globalRules = new LinkedHashSet<>();
        globalRules.add(factory.add("universal-first"));
        globalRules.add(factory.add("universal-second"));
        var priorGlobal = universal.put(source, globalRules);
        var priorLocal = byBlock.put(source, Map.of(state.getBlock(), Set.of(factory.add("block"))));
        try {
            tick.invokeExact(source, state, level, pos);
            require(order.equals(java.util.List.of("universal-first", "universal-second", "block")), "growth order");
            require(suppliers.size() == 3 && suppliers.get(0) == suppliers.get(1) && suppliers.get(1) == suppliers.get(2),
                    "memoization shared by all rules for one tick");
            require(randomDraws.size() == 3, "each growth rule draws RNG once");
        } finally {
            if (priorGlobal == null) universal.remove(source); else universal.put(source, priorGlobal);
            if (priorLocal == null) byBlock.remove(source); else byBlock.put(source, priorLocal);
        }
        Files.writeString(Path.of("weathering-growth.json"), String.format(java.util.Locale.ROOT,
                "{\"calls_per_batch\":250000,\"median_empty_ns\":%.3f,\"p95_empty_ns\":%.3f,\"rule_count\":%d}%n",
                elapsed[9], elapsed[17], order.size()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

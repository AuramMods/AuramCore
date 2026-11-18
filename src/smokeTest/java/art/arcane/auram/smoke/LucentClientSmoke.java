package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import jdk.jfr.Recording;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.glfw.GLFW;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/** Drive the installed lighting engine against real client blocks with deterministic inputs. */
@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class LucentClientSmoke {
    private static final String MODE = "memory-lucent";
    private static final List<Map<String, Object>> states = new ArrayList<>();
    private static final List<Map<String, Object>> propagation = new ArrayList<>();
    private static CompletableFuture<BlockPos> arena;
    private static int phase, ticks;
    private static LightAccess light;
    private static Map<String, Object> benchmark;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !MODE.equals(System.getProperty("auram.smoke")) || phase == 5) return;
        var client = Minecraft.getInstance();
        if (client.getOverlay() != null) return;
        try {
            if (phase == 0 && client.screen instanceof AccessibilityOnboardingScreen) {
                client.options.onboardAccessibility = false;
                client.setScreen(new TitleScreen());
                return;
            }
            if (++ticks % 200 == 0) System.out.println("Lucent smoke phase " + phase + " screen " + client.screen);
            if (phase == 0 && client.screen instanceof TitleScreen) {
                if (GLFW.glfwGetWindowAttrib(client.getWindow().getWindow(), GLFW.GLFW_VISIBLE) != GLFW.GLFW_FALSE
                        || client.options.getSoundSourceVolume(SoundSource.MASTER) != 0) {
                    throw new AssertionError("Lucent fixture must run hidden and muted");
                }
                phase = 1;
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                client.createWorldOpenFlows().createFreshLevel("auram-lucent-qa",
                        new LevelSettings("Auram Lucent QA", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, rules, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(104729L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            } else if (phase == 1 && client.level != null && client.player != null && client.getSingleplayerServer() != null) {
                phase = 2;
                light = new LightAccess();
                arena = prepareArena(client, Level.OVERWORLD);
            } else if (phase == 2 && ready(client, Level.OVERWORLD)) {
                BlockPos base = arena.join();
                if (!isAtArena(client, base)) return;
                runPropagation(client, base);
                phase = 3;
                arena = prepareArena(client, Level.NETHER);
            } else if (phase == 3 && ready(client, Level.NETHER)) {
                BlockPos base = arena.join();
                if (!isAtArena(client, base)) return;
                captureTick(client, "nether-after-transition", base.above(), true);
                phase = 4;
                arena = prepareArena(client, Level.OVERWORLD);
            } else if (phase == 4 && ready(client, Level.OVERWORLD)) {
                BlockPos base = arena.join();
                if (!isAtArena(client, base)) return;
                captureTick(client, "overworld-after-return", base.above(), true);
                Files.writeString(Path.of("lucent-client.json"), new GsonBuilder().setPrettyPrinting().create()
                        .toJson(Map.of("ticks", states, "propagation", propagation, "benchmark", benchmark)) + "\n");
                Files.writeString(Path.of(MODE + "-pass.txt"), "PASS installed lighting engine, deterministic route and dimension transitions\n");
                phase = 5;
                client.stop();
            }
        } catch (Throwable error) {
            phase = 5;
            error.printStackTrace();
            try {
                Files.writeString(Path.of(MODE + "-failed.txt"), error.toString());
            } catch (Exception writeError) {
                writeError.printStackTrace();
            }
            client.stop();
        }
    }

    private static CompletableFuture<BlockPos> prepareArena(Minecraft client, net.minecraft.resources.ResourceKey<Level> key) {
        var server = client.getSingleplayerServer();
        return server.submit(() -> {
            var player = server.getPlayerList().getPlayers().get(0);
            var world = server.getLevel(key);
            BlockPos base = new BlockPos(8, world.getMaxBuildHeight() - 32, 8);
            for (var pos : BlockPos.betweenClosed(base.offset(-10, 0, -10), base.offset(10, 5, 10))) {
                var state = pos.getY() == base.getY() ? Blocks.SMOOTH_STONE : Blocks.AIR;
                if (!world.getBlockState(pos).is(state)) world.setBlock(pos, state.defaultBlockState(), 3);
            }
            player.getInventory().setItem(0, new ItemStack(Items.TORCH));
            player.getInventory().selected = 0;
            player.teleportTo(world, base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5, 0, 0);
            return base;
        });
    }

    private static boolean ready(Minecraft client, net.minecraft.resources.ResourceKey<Level> key) {
        return arena.isDone() && client.player != null && client.level != null && client.level.dimension().equals(key);
    }

    private static boolean isAtArena(Minecraft client, BlockPos base) {
        return Math.abs(client.player.getY() - base.getY() - 1) < 2
                && client.level.getBlockState(base).is(Blocks.SMOOTH_STONE);
    }

    private static void runPropagation(Minecraft client, BlockPos base) throws Throwable {
        BlockPos origin = base.above(2), second = origin.offset(2, 0, 1);
        Object firstSource = light.source(origin);
        Object secondSource = light.source(second);
        for (boolean smooth : new boolean[]{false, true}) {
            light.smooth(smooth);
            Map<BlockPos, Object> open = light.propagate(origin, second, firstSource, secondSource);
            int overlaps = 0;
            for (Object value : open.values()) if (((List<?>) light.sources.get(value)).size() > 1) overlaps++;
            if (smooth && overlaps == 0) throw new AssertionError("Overlapping lights did not exercise list mutation");
            propagation.add(Map.of("smooth", smooth, "scene", "open", "positions", open.size(),
                    "overlaps", overlaps, "fingerprint", light.fingerprint(open)));
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(1, -1, -4), origin.offset(1, 2, 4))) {
                client.level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            }
            Map<BlockPos, Object> occluded = light.propagate(origin, second, firstSource, secondSource);
            String opaque = light.fingerprint(occluded);
            if (opaque.equals(light.fingerprint(open))) throw new AssertionError("Occlusion did not change propagation");
            propagation.add(Map.of("smooth", smooth, "scene", "wall", "positions", occluded.size(), "fingerprint", opaque));
            captureTick(client, smooth ? "smooth-before-move" : "before-move", base.above(), true);
            captureTick(client, smooth ? "smooth-after-move" : "after-move", base.offset(4, 1, 0), false);
            BlockPos changed = base.above(2);
            client.level.setBlock(changed, Blocks.STONE.defaultBlockState(), 3);
            light.blockChanged.invokeExact(changed, Blocks.STONE.defaultBlockState());
            captureTick(client, smooth ? "smooth-block-changed" : "block-changed", base.offset(4, 1, 0), false);
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(0, -1, -4), origin.offset(1, 2, 4))) {
                client.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        benchmark = timePropagation(origin, second, firstSource, secondSource);
    }

    private static void captureTick(Minecraft client, String name, BlockPos position, boolean reset) throws Throwable {
        client.player.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        client.player.getInventory().selected = 0;
        client.player.getInventory().setItem(0, new ItemStack(Items.TORCH));
        if (reset) light.clear.invokeExact();
        light.tick.invokeExact();
        Map<BlockPos, Object> lights = light.current();
        Set<?> sections = (Set<?>) light.dirty.get(null);
        if (lights.isEmpty() || sections.isEmpty()) throw new AssertionError("No lighting or dirty sections in " + name);
        states.add(Map.of("scene", name, "dimension", client.level.dimension().location().toString(),
                "lights", lights.size(), "fingerprint", light.fingerprint(lights),
                "dirty_sections", sections.stream().map(Object::toString).sorted().toList()));
    }

    private static Map<String, Object> timePropagation(BlockPos first, BlockPos second, Object a, Object b) throws Throwable {
        var allocation = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        allocation.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        long[] time = new long[20], bytes = new long[20];
        int perBatch = 60, checksum = 0;
        for (int i = 0; i < 5; i++) for (int repeat = 0; repeat < perBatch; repeat++)
            checksum += light.propagate(first, second, a, b).size();
        try (Recording recording = new Recording()) {
            recording.enable("jdk.ObjectAllocationSample").with("throttle", "1000/s").withStackTrace();
            recording.start();
            for (int i = 0; i < time.length; i++) {
                long before = allocation.getThreadAllocatedBytes(thread), start = System.nanoTime();
                for (int repeat = 0; repeat < perBatch; repeat++) checksum += light.propagate(first, second, a, b).size();
                time[i] = System.nanoTime() - start;
                bytes[i] = allocation.getThreadAllocatedBytes(thread) - before;
            }
            recording.stop();
            recording.dump(Path.of("lucent-propagation.jfr"));
        }
        return Map.of("time_nanos", time, "allocated_bytes", bytes, "per_batch", perBatch, "checksum", checksum);
    }

    private static final class LightAccess {
        final MethodHandle add, tick, clear, createSource, blockChanged;
        final Field current, dirty, sources, strength;
        final Field x, y, z;
        final Object smooth;

        LightAccess() throws ReflectiveOperationException {
            String pkg = "com.legacy.lucent.core.dynamic_lighting.";
            Class<?> engine = Class.forName(pkg + "DynamicLightingEngine");
            Class<?> data = Class.forName(pkg + "LightData");
            Class<?> source = Class.forName(pkg + "LightData$SourcePos");
            var ctor = source.getDeclaredConstructor(double.class, double.class, double.class);
            ctor.setAccessible(true);
            var lookup = MethodHandles.lookup();
            createSource = lookup.unreflectConstructor(ctor).asType(MethodType.methodType(Object.class, double.class, double.class, double.class));
            var method = engine.getDeclaredMethod("addLights", Map.class, BlockPos.class, int.class, source);
            method.setAccessible(true);
            add = lookup.unreflect(method).asType(MethodType.methodType(void.class, Map.class, BlockPos.class, int.class, Object.class));
            var tickMethod = engine.getDeclaredMethod("tick");
            tickMethod.setAccessible(true);
            tick = lookup.unreflect(tickMethod);
            clear = lookup.unreflect(engine.getMethod("clearAllData"));
            blockChanged = lookup.unreflect(engine.getMethod("blockChanged", BlockPos.class, net.minecraft.world.level.block.state.BlockState.class));
            current = field(engine, "lightData");
            dirty = field(engine, "sectionsToUpdate");
            sources = field(data, "sourcePoses");
            strength = field(data, "lightLevel");
            x = field(source, "x");
            y = field(source, "y");
            z = field(source, "z");
            Object config = Class.forName("com.legacy.lucent.core.LucentConfig").getField("CLIENT").get(null);
            smooth = field(config.getClass(), "smoothBlending").get(config);
        }

        void smooth(boolean enabled) throws ReflectiveOperationException {
            smooth.getClass().getMethod("set", Object.class).invoke(smooth, enabled);
        }

        Map<BlockPos, Object> propagate(BlockPos a, BlockPos b, Object sa, Object sb) throws Throwable {
            Map<BlockPos, Object> map = new HashMap<>();
            add.invokeExact(map, a, 12, sa);
            add.invokeExact(map, b, 12, sb);
            return map;
        }

        Object source(BlockPos at) throws Throwable {
            return (Object) createSource.invokeExact(at.getX() + 0.25, at.getY() + 0.5, at.getZ() + 0.75);
        }

        @SuppressWarnings("unchecked")
        Map<BlockPos, Object> current() throws IllegalAccessException {
            return (Map<BlockPos, Object>) current.get(null);
        }

        String fingerprint(Map<BlockPos, Object> map) throws Exception {
            Map<Long, Object> ordered = new TreeMap<>();
            map.forEach((pos, value) -> ordered.put(pos.asLong(), value));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (var entry : ordered.entrySet()) {
                Object value = entry.getValue();
                digest.update((entry.getKey() + ":" + strength.getInt(value) + ":").getBytes(StandardCharsets.UTF_8));
                for (Object source : (List<?>) sources.get(value))
                    digest.update((x.getDouble(source) + "," + y.getDouble(source) + "," + z.getDouble(source) + ";")
                            .getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        }

        private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
    }
}

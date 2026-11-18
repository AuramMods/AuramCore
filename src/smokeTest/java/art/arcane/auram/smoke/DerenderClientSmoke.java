package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import jdk.jfr.Recording;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Exercise the production-mapped target and the real shader/render path. */
@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class DerenderClientSmoke {
    private static final String MODE = "memory-derender";
    private static final String[] MODS = {"oculus", "immediatelyfast", "fabric_renderer_indigo",
            "draconicevolution", "auram_test_missing_mod"};
    private static final String[] ITEMS = {"draconic_pickaxe", "draconic_sword", "draconic_bow"};
    private static final List<Map<String, Object>> renderChecks = new ArrayList<>();
    private static CompletableFuture<Arena> arena;
    private static MethodHandle shaderInUse;
    private static int phase, stage, scene, frames;
    private static int energyCalls, reactorCalls;
    private static Recording renderRecording;
    private static long previousFrame;
    private static final List<Long> frameNanos = new ArrayList<>();
    private static Map<String, Object> lookup;

    public static void energyCoreRendered() {
        if (phase == 3) energyCalls++;
    }

    public static void reactorRendered() {
        if (phase == 3) reactorCalls++;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !MODE.equals(System.getProperty("auram.smoke")) || phase == 4) return;
        Minecraft client = Minecraft.getInstance();
        try {
            if (phase == 0 && client.getOverlay() == null && client.screen instanceof TitleScreen) {
                require(GLFW.glfwGetWindowAttrib(client.getWindow().getWindow(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                        "Client window is hidden");
                require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Client audio is muted");
                lookup = benchmark();
                write("derender-lookup.json", lookup);
                if (Boolean.getBoolean("auram.smoke.lookupOnly")) {
                    finish(client);
                    return;
                }
                phase = 1;
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                LevelSettings settings = new LevelSettings("Auram Render QA", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel("auram-render-qa", settings,
                        new WorldOptions(104729L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            } else if (phase == 1 && client.level != null && client.player != null && client.getSingleplayerServer() != null) {
                shaderInUse = MethodHandles.publicLookup().findStatic(Class.forName("com.derenderpatcher.compat.ShaderCompat"),
                        "isShaderPackInUse", MethodType.methodType(boolean.class));
                var server = client.getSingleplayerServer();
                arena = server.submit(() -> {
                    var player = server.getPlayerList().getPlayers().get(0);
                    var level = player.serverLevel();
                    // This pack overrides the nominal flat preset. Place the
                    // fixture above its terrain so the same view is unobstructed.
                    var base = player.blockPosition().atY(level.getMaxBuildHeight() - 32);
                    level.setDayTime(6000);
                    for (var pos : BlockPos.betweenClosed(base.offset(-8, 0, -8), base.offset(8, 0, 8))) {
                        level.setBlock(pos, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                    }
                    for (var pos : BlockPos.betweenClosed(base.offset(-8, 1, -8), base.offset(8, 5, 8))) {
                        if (!level.isEmptyBlock(pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                    var corePos = base.offset(-2, 1, 5);
                    var reactorPos = base.offset(2, 1, 5);
                    for (var pos : List.of(corePos, reactorPos)) {
                        String id = pos.equals(corePos) ? "energy_core" : "reactor_core";
                        var block = ForgeRegistries.BLOCKS.getValue(itemId(id));
                        require(block != null && block != net.minecraft.world.level.block.Blocks.AIR, id + " registered");
                        level.setBlockAndUpdate(pos, block.defaultBlockState());
                        require(level.getBlockEntity(pos) != null, id + " block entity placed");
                    }
                    player.connection.teleport(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5, 0, 10);
                    for (int i = 0; i < ITEMS.length; i++) {
                        var item = ForgeRegistries.ITEMS.getValue(itemId(ITEMS[i]));
                        require(item != null && item != net.minecraft.world.item.Items.AIR, ITEMS[i] + " registered");
                        player.getInventory().setItem(i, item.getDefaultInstance());
                    }
                    return new Arena(corePos, reactorPos);
                });
                phase = 2;
            } else if (phase == 2 && arena.isDone() && ready(client)) {
                Arena positions = arena.join();
                var core = client.level.getBlockEntity(positions.core());
                var reactor = client.level.getBlockEntity(positions.reactor());
                if (core == null || reactor == null) return;
                // Drive the active renderer locally in the private scene. The
                // real server-side block entities stay offline.
                var active = core.getClass().getField("active").get(core);
                active.getClass().getMethod("set", boolean.class).invoke(active, true);
                var temperature = reactor.getClass().getField("temperature").get(reactor);
                temperature.getClass().getMethod("set", double.class).invoke(temperature, 3000.0);
                client.gui.getChat().clearMessages(false);
                renderRecording = new Recording();
                renderRecording.enable("jdk.ObjectAllocationSample").with("throttle", "200/s").withStackTrace();
                renderRecording.enable("jdk.GCPhasePause");
                renderRecording.start();
                configureScene(client);
                phase = 3;
            }
        } catch (Throwable error) {
            fail(client, error);
        }
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase != 3) return;
        Minecraft client = Minecraft.getInstance();
        if (!ready(client)) return;
        long now = System.nanoTime();
        if (previousFrame != 0 && frames > 10) frameNanos.add(now - previousFrame);
        previousFrame = now;
        if (++frames < 45) return;
        try {
            boolean expectedShader = stage == 1 || stage == 3;
            require((boolean) shaderInUse.invokeExact() == expectedShader, "Live shader state for stage " + stage);
            if (scene == 6) require(energyCalls > 0 && reactorCalls > 0, "Both core renderers executed at stage " + stage);
            String name = stage + "-" + scene;
            try (NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                image.writeToFile(Path.of("derender-" + name + ".png"));
            }
            renderChecks.add(Map.of("file", "derender-" + name + ".png", "shader", expectedShader,
                    "energy_calls", energyCalls, "reactor_calls", reactorCalls,
                    "camera", client.options.getCameraType().name(), "item", String.valueOf(
                            ForgeRegistries.ITEMS.getKey(client.player.getMainHandItem().getItem()))));
            if (++scene == 7) {
                scene = 0;
                if (++stage == 4) {
                    finish(client);
                    return;
                }
            }
            configureScene(client);
        } catch (Throwable error) {
            fail(client, error);
        }
    }

    private static Map<String, Object> benchmark() throws Throwable {
        MethodHandle check = MethodHandles.publicLookup().findStatic(Class.forName("com.derenderpatcher.compat.CompatMods"),
                "isModLoaded", MethodType.methodType(boolean.class, String.class));
        boolean[] expected = new boolean[MODS.length];
        for (int i = 0; i < MODS.length; i++) {
            expected[i] = ModList.get().isLoaded(MODS[i]);
            require((boolean) check.invokeExact(MODS[i]) == expected[i], "Lookup equivalence: " + MODS[i]);
        }
        var allocation = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        allocation.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        long[] timings = new long[20], bytes = new long[20];
        int operations = 20_000;
        int expectedHits = 0;
        for (boolean value : expected) if (value) expectedHits += operations / MODS.length;
        for (int i = 0; i < 5; i++) require(calls(check, operations) == expectedHits, "Warmup result");
        try (Recording recording = new Recording()) {
            recording.enable("jdk.ObjectAllocationSample").with("throttle", "1000/s").withStackTrace();
            recording.start();
            for (int i = 0; i < timings.length; i++) {
                long before = allocation.getThreadAllocatedBytes(thread);
                long start = System.nanoTime();
                int hits = calls(check, operations);
                timings[i] = System.nanoTime() - start;
                bytes[i] = allocation.getThreadAllocatedBytes(thread) - before;
                require(hits == expectedHits, "Measured lookup result");
            }
            recording.stop();
            recording.dump(Path.of("derender-lookup.jfr"));
        }
        return Map.of("operations_per_batch", operations, "time_nanos", timings, "allocated_bytes", bytes,
                "lookup_ids", MODS, "lookup_results", expected);
    }

    private static int calls(MethodHandle check, int operations) throws Throwable {
        int hits = 0;
        for (int i = 0; i < operations; i++) {
            if ((boolean) check.invokeExact(MODS[i % MODS.length])) hits++;
        }
        return hits;
    }

    private static void configureScene(Minecraft client) throws ReflectiveOperationException {
        if (scene == 0 && stage != 0) {
            Class.forName("net.irisshaders.iris.Iris").getMethod("toggleShaders", Minecraft.class, boolean.class)
                    .invoke(null, client, stage % 2 == 1);
        }
        client.player.getInventory().selected = scene == 6 ? 8 : scene / 2;
        client.options.setCameraType(scene < 6 && scene % 2 == 1 ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
        if (client.screen != null) client.setScreen(null);
        frames = 0;
        previousFrame = 0;
        energyCalls = 0;
        reactorCalls = 0;
    }

    private static boolean ready(Minecraft client) {
        return client.level != null && client.player != null && client.getOverlay() == null;
    }

    private static ResourceLocation itemId(String name) {
        return ResourceLocation.fromNamespaceAndPath("draconicevolution", name);
    }

    private static void write(String name, Object value) throws Exception {
        Files.writeString(Path.of(name), new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n");
    }

    private static void finish(Minecraft client) throws Exception {
        phase = 4;
        if (renderRecording != null) {
            renderRecording.stop();
            renderRecording.dump(Path.of("derender-render.jfr"));
            renderRecording.close();
        }
        write("derender-client.json", Map.of("lookup", lookup, "renders", renderChecks, "frame_nanos", frameNanos));
        Files.writeString(Path.of(MODE + "-pass.txt"), "PASS lookup parity, allocation recording and requested render sequence\n");
        client.stop();
    }

    private static void fail(Minecraft client, Throwable error) {
        phase = 4;
        error.printStackTrace();
        try {
            Files.writeString(Path.of(MODE + "-failed.txt"), error.toString());
        } catch (Exception writeError) {
            writeError.printStackTrace();
        }
        client.stop();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Arena(BlockPos core, BlockPos reactor) { }
}

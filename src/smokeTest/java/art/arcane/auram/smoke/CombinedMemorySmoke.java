package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Repeat a join, exploration, dimension change, reconnect, and resource reload against a private server. */
@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class CombinedMemorySmoke {
    private static final String MODE = "memory-combined";
    private static final String WORLD = "auram-full-memory-qa";
    private static final String ADDRESS = System.getProperty("auram.smoke.address");
    private static final String SAVED_WORLD = System.getProperty("auram.smoke.memorySavedWorld");
    private static final long DWELL_NANOS = 1_000_000_000L
            * Integer.getInteger("auram.smoke.memoryDwellSeconds", 60);
    private static final List<Map<String, Object>> checkpoints = new ArrayList<>();
    private static final List<Long> frames = new ArrayList<>();
    private static long previousFrame;
    private static long settledAt;
    private static int stage, ticks;
    private static CompletableFuture<Void> reload;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !MODE.equals(System.getProperty("auram.smoke")) || stage == 10) return;
        Minecraft client = Minecraft.getInstance();
        if (client.getOverlay() != null) return;
        try {
            if (client.screen instanceof DisconnectedScreen) {
                throw new IllegalStateException("Connection ended before route checkpoint " + stage);
            }
            if (SAVED_WORLD != null) {
                savedWorldRoute(client);
                return;
            }
            switch (stage) {
                case 0 -> {
                    if (!(client.screen instanceof TitleScreen) || ++ticks < 40) return;
                    verifyHiddenAndMuted(client);
                    record("startup", client);
                    join(client, true);
                }
                case 1 -> {
                    if (!settled(client, Level.OVERWORLD, null, null)) return;
                    record("joined", client);
                    client.getConnection().sendCommand("tp 512 170 512");
                }
                case 2 -> {
                    if (!settled(client, Level.OVERWORLD, 512, 512)) return;
                    record("overworld-east", client);
                    client.getConnection().sendCommand("tp -768 180 640");
                }
                case 3 -> {
                    if (!settled(client, Level.OVERWORLD, -768, 640)) return;
                    record("overworld-west", client);
                    client.getConnection().sendCommand("execute in minecraft:the_nether run tp @s 48 110 48");
                }
                case 4 -> {
                    if (!settled(client, Level.NETHER, 48, 48)) return;
                    record("nether", client);
                    client.getConnection().sendCommand("execute in minecraft:overworld run tp @s 0 160 0");
                }
                case 5 -> {
                    if (!settled(client, Level.OVERWORLD, 0, 0)) return;
                    record("overworld-return", client);
                    client.level.disconnect();
                    client.clearLevel(new TitleScreen());
                }
                case 6 -> {
                    if (client.level != null || !(client.screen instanceof TitleScreen) || ++ticks < 80) return;
                    record("disconnected", client);
                    join(client, false);
                }
                case 7 -> {
                    if (!settled(client, Level.OVERWORLD, 0, 0)) return;
                    record("rejoined", client);
                    reload = client.reloadResourcePacks();
                }
                case 8 -> {
                    if (!reload.isDone() || !settled(client, Level.OVERWORLD, 0, 0)) return;
                    reload.join();
                    record("reloaded", client);
                    System.gc();
                }
                case 9 -> {
                    if (!settled(client, Level.OVERWORLD, 0, 0)) return;
                    record("post-full-gc", client);
                    Files.writeString(Path.of(MODE + "-pass.txt"), "PASS full-pack memory route\n");
                    client.stop();
                }
                default -> throw new IllegalStateException("Unexpected route stage " + stage);
            }
            stage++;
            ticks = 0;
            previousFrame = 0;
        } catch (Throwable error) {
            stage = 10;
            error.printStackTrace();
            client.stop();
        }
    }

    private static void savedWorldRoute(Minecraft client) throws Exception {
        switch (stage) {
            case 0 -> {
                if (!(client.screen instanceof TitleScreen) || ++ticks < 40) return;
                verifyHiddenAndMuted(client);
                record("startup", client);
                client.createWorldOpenFlows().loadLevel(new TitleScreen(), SAVED_WORLD);
            }
            case 1 -> {
                if (client.level == null || client.getSingleplayerServer() == null
                        || !settled(client, client.level.dimension(), null, null)) return;
                teleport(client, Level.NETHER, 124, 66, 856);
            }
            case 2 -> {
                if (!warmed(client, Level.NETHER, 124, 856)) return;
                record("nether-warm", client);
            }
            case 3 -> {
                if (Boolean.getBoolean("auram.smoke.waitForNetherHeapDump")
                        && !Files.isRegularFile(Path.of("nether-heap-dump-complete.txt"))) return;
                teleport(client, Level.OVERWORLD, 992, 155, 6848);
            }
            case 4 -> {
                if (!settled(client, Level.OVERWORLD, 992, 6848)) return;
                record("overworld-recording-start", client);
            }
            case 5 -> {
                if (!warmed(client, Level.OVERWORLD, 992, 6848)) return;
                record("overworld-recording-end", client);
                teleport(client, Level.NETHER, 124, 66, 856);
            }
            case 6 -> {
                if (!settled(client, Level.NETHER, 124, 856)) return;
                record("nether-return", client);
                reload = client.reloadResourcePacks();
            }
            case 7 -> {
                if (!reload.isDone() || !settled(client, Level.NETHER, 124, 856)) return;
                reload.join();
                record("reloaded", client);
                System.gc();
            }
            case 8 -> {
                if (!settled(client, Level.NETHER, 124, 856)) return;
                record("post-full-gc", client);
                Files.writeString(Path.of(MODE + "-pass.txt"), "PASS saved-world memory route\n");
                stage = 10;
                client.stop();
                return;
            }
            default -> throw new IllegalStateException("Unexpected saved-world route stage " + stage);
        }
        stage++;
        ticks = 0;
        settledAt = stage == 5 ? System.nanoTime() : 0;
        previousFrame = 0;
    }

    private static boolean warmed(Minecraft client, ResourceKey<Level> dimension, int x, int z) {
        if (!settled(client, dimension, x, z)) return false;
        long now = System.nanoTime();
        if (settledAt == 0) settledAt = now;
        return now - settledAt >= DWELL_NANOS;
    }

    private static void teleport(Minecraft client, ResourceKey<Level> dimension, int x, int y, int z) {
        var server = client.getSingleplayerServer();
        var uuid = client.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(uuid);
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(server.getLevel(dimension), x + 0.5, y, z + 0.5, 0, 0);
        });
    }

    private static void verifyHiddenAndMuted(Minecraft client) {
        if (GLFW.glfwGetWindowAttrib(client.getWindow().getWindow(), GLFW.GLFW_VISIBLE) != GLFW.GLFW_FALSE
                || client.options.getSoundSourceVolume(SoundSource.MASTER) != 0) {
            throw new AssertionError("The client must be hidden and muted");
        }
    }

    @SubscribeEvent
    public static void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !MODE.equals(System.getProperty("auram.smoke"))) return;
        long now = System.nanoTime();
        if (previousFrame != 0) frames.add(now - previousFrame);
        previousFrame = now;
    }

    private static void join(Minecraft client, boolean firstJoin) {
        if (ADDRESS != null) {
            ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(ADDRESS),
                    new ServerData("Private Auram fixture", ADDRESS, false), false);
        } else if (firstJoin) {
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            client.createWorldOpenFlows().createFreshLevel(WORLD,
                    new LevelSettings("Auram Memory QA", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                            true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(104729L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        } else {
            client.createWorldOpenFlows().loadLevel(new TitleScreen(), WORLD);
        }
    }

    private static boolean settled(Minecraft client, ResourceKey<Level> dimension, Integer x, Integer z) {
        if (client.level == null || client.player == null || client.isLocalServer() != (ADDRESS == null) || !client.level.dimension().equals(dimension)
                || (x != null && (Math.abs(client.player.getX() - x) > 3 || Math.abs(client.player.getZ() - z) > 3))
                || !client.level.hasChunkAt(client.player.blockPosition())
                || client.level.getChunkSource().getLoadedChunksCount() < 25) return false;
        if (client.screen != null) client.setScreen(null);
        return ++ticks >= 100;
    }

    private static void record(String name, Minecraft client) throws Exception {
        var usage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        checkpoints.add(Map.of("stage", name, "uptime_ms", ManagementFactory.getRuntimeMXBean().getUptime(),
                "used_bytes", usage.getUsed(), "committed_bytes", usage.getCommitted(), "frames_ns", List.copyOf(frames),
                "chunks", client.level == null ? 0 : client.level.getChunkSource().getLoadedChunksCount(),
                "dimension", client.level == null ? "" : client.level.dimension().location().toString()));
        frames.clear();
        Files.writeString(Path.of("combined-client.json"), new GsonBuilder().setPrettyPrinting().create().toJson(checkpoints) + "\n");
        Files.writeString(Path.of("combined-" + name + ".txt"), name + "\n");
        System.out.println("Completed memory route checkpoint " + name);
    }
}

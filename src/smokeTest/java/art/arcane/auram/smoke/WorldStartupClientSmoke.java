package art.arcane.auram.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class WorldStartupClientSmoke {
    private static final String SAVE = "auram-world-startup-smoke";
    private static final String MODE = "world-startup";
    private static int phase;
    private static long start;
    private static long freshMillis;

    private WorldStartupClientSmoke() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!MODE.equals(System.getProperty("auram.smoke")) || event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (phase != 0 || client.getOverlay() != null || client.screen == null) return;
        try {
            System.out.println("Auram world startup smoke opening from " + client.screen.getClass().getSimpleName());
            phase = 1;
            start = System.nanoTime();
            boolean lan = Integer.getInteger("auram.smoke.lanPort", 0) != 0;
            var settings = new LevelSettings("Auram Startup QA", GameType.CREATIVE, false,
                    Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            client.createWorldOpenFlows().createFreshLevel(SAVE, settings,
                    new WorldOptions(Long.getLong("auram.smoke.seed", 104729L), !lan, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(lan ? WorldPresets.FLAT : WorldPresets.NORMAL)
                            .value().createWorldDimensions());
        } catch (Throwable error) {
            fail(client, error);
        }
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (!MODE.equals(System.getProperty("auram.smoke")) || event.phase != TickEvent.Phase.END
                || (phase != 1 && phase != 2)) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.screen != null || client.getOverlay() != null) return;
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        try {
            if (phase == 1) {
                freshMillis = elapsedMillis;
                phase = 3;
                System.out.println("Auram world startup smoke fresh_ms=" + freshMillis);
                System.out.println("Auram integrated scheduling: "
                        + client.getSingleplayerServer().getRunningThread().getPriority());
                if (Boolean.getBoolean("auram.smoke.smoothScroll")) SmoothScrollSmoke.run(client);
                if (Boolean.getBoolean("auram.smoke.transitions")) TransitionSmoke.run(client);
                int lanPort = Integer.getInteger("auram.smoke.lanPort", 0);
                if (lanPort != 0) {
                    var server = client.getSingleplayerServer();
                    server.setUsesAuthentication(false);
                    server.getConnection().startTcpServerListener(InetAddress.getLoopbackAddress(), lanPort);
                    Files.writeString(Path.of("auram-lan-ready.txt"), Integer.toString(lanPort));
                    return;
                }
                if (Boolean.getBoolean("auram.smoke.verifyRings")) {
                    var server = client.getSingleplayerServer();
                    CompletableFuture.runAsync(() -> {
                        try {
                            RingSearchSmoke.run(server.overworld());
                        } catch (Exception error) {
                            throw new CompletionException(error);
                        }
                    }, server).whenComplete((unused, error) -> client.tell(() -> {
                        if (error == null) reopen(client);
                        else fail(client, error);
                    }));
                } else {
                    client.tell(() -> reopen(client));
                }
            } else {
                phase = 3;
                System.out.println("Auram integrated scheduling after reopen: "
                        + client.getSingleplayerServer().getRunningThread().getPriority());
                Files.writeString(Path.of("auram-world-startup-pass.txt"),
                        "fresh_ms=%d%nreopen_ms=%d%n".formatted(freshMillis, elapsedMillis));
                client.stop();
            }
        } catch (Throwable error) {
            fail(client, error);
        }
    }

    private static void reopen(Minecraft client) {
        try {
            var title = new TitleScreen();
            client.level.disconnect();
            client.clearLevel(title);
            start = System.nanoTime();
            phase = 2;
            client.createWorldOpenFlows().loadLevel(title, SAVE);
        } catch (Throwable error) {
            fail(client, error);
        }
    }

    private static void fail(Minecraft client, Throwable error) {
        phase = 3;
        error.printStackTrace();
        try { Files.writeString(Path.of("auram-world-startup-failed.txt"), error.toString()); }
        catch (Exception writeError) { error.addSuppressed(writeError); }
        client.stop();
    }
}

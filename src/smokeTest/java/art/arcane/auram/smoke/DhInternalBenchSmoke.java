package art.arcane.auram.smoke;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = "auram_patch_smoke")
public final class DhInternalBenchSmoke {
    private static MinecraftServer active;
    private static long started;
    private static long startedMillis;
    private static long tickStarted;
    private static final List<String> ticks = new ArrayList<>();

    private DhInternalBenchSmoke() {}

    public static void start(MinecraftServer server) {
        server.execute(() -> {
            active = server;
            AquiferReuseProbe.beginColdRun();
            started = System.nanoTime();
            startedMillis = System.currentTimeMillis();
            LogUtils.getLogger().info("Auram DH benchmark start");
        });
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.getServer() != active) return;
        long now = System.nanoTime();
        if (event.phase == TickEvent.Phase.START) {
            tickStarted = now;
        } else if (tickStarted != 0) {
            ticks.add((now - started) + "," + (now - tickStarted));
            tickStarted = 0;
        }
    }

    public static CompletableFuture<Void> finish(MinecraftServer server) {
        return server.submit(() -> {
            active = null;
            long finishedMillis = System.currentTimeMillis();
            try {
                AquiferReuseProbe.write(Path.of("worldgen-stage-cpu.csv"));
                Files.writeString(Path.of("worldgen-cold-window.csv"), "start_epoch_ms,end_epoch_ms\n"
                        + startedMillis + "," + finishedMillis + "\n");
                var lines = new ArrayList<String>(ticks.size() + 1);
                lines.add("elapsed_ns,tick_ns");
                lines.addAll(ticks);
                Files.write(Path.of("auram-dh-ticks.csv"), lines);
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
            LogUtils.getLogger().info("Auram DH benchmark end");
        });
    }
}

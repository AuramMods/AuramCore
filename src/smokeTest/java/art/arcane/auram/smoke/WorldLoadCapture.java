package art.arcane.auram.smoke;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

@Mod.EventBusSubscriber(modid = "auram_patch_smoke")
public final class WorldLoadCapture {
    public static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(Integer.getInteger("auram.smoke.worldLoadSeconds", 0));
    private static final TickSeries SERVER = new TickSeries();
    private static final LongAdder MAP_NANOS = new LongAdder();
    private static final LongAdder MAP_STATES = new LongAdder();
    private static final LongAdder NEIGHBOR_NANOS = new LongAdder();
    private static int serverJoin;
    private static long joinAt;
    private static long tickStart;

    private WorldLoadCapture() {}

    public static void mark(String name) {
        if (WINDOW_NANOS > 0) {
            System.out.println("AURAM_WORLD_LOAD " + name + " epoch_ms=" + System.currentTimeMillis());
        }
    }

    public static void worldEditStateMap(long nanos, int states) {
        MAP_NANOS.add(nanos);
        MAP_STATES.add(states);
    }

    public static void worldEditNeighborTable(long nanos) {
        NEIGHBOR_NANOS.add(nanos);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void serverStartup(ServerAboutToStartEvent event) {
        mark("server_about_to_start");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverReady(ServerStartedEvent event) {
        mark("server_started_event_complete");
        if (WINDOW_NANOS > 0) {
            long stateMaps = MAP_NANOS.sumThenReset();
            long states = MAP_STATES.sumThenReset();
            long neighbors = NEIGHBOR_NANOS.sumThenReset();
            System.out.println("AURAM_WORLD_LOAD worldedit_tables states=" + states + " map_ms=" + stateMaps / 1_000_000.0
                    + " neighbor_ms=" + neighbors / 1_000_000.0);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (WINDOW_NANOS <= 0 || !(event.getEntity() instanceof ServerPlayer)) return;
        finishServerCapture();
        joinAt = System.nanoTime();
        serverJoin++;
        mark("player_admitted_" + serverJoin);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beginTick(TickEvent.ServerTickEvent event) {
        if (joinAt != 0 && event.phase == TickEvent.Phase.START) tickStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void endTick(TickEvent.ServerTickEvent event) {
        if (joinAt == 0 || event.phase != TickEvent.Phase.END) return;
        long now = System.nanoTime();
        if (tickStart >= joinAt) SERVER.add(now - joinAt, now - tickStart);
        if (now - joinAt >= WINDOW_NANOS) finishServerCapture();
    }

    @SubscribeEvent
    public static void serverLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer) finishServerCapture();
    }

    private static void finishServerCapture() {
        if (joinAt == 0) return;
        SERVER.save("world-load-server-ticks-" + serverJoin + ".csv", "tick_ms");
        joinAt = 0;
    }

    static final class TickSeries {
        private final List<String> rows = new ArrayList<>();

        void add(long elapsed, long duration) {
            rows.add(Double.toString(elapsed / 1_000_000.0) + "," + duration / 1_000_000.0);
        }

        void save(String file, String column) {
            try {
                Files.writeString(Path.of(file), "elapsed_ms," + column + "\n" + String.join("\n", rows) + "\n");
            } catch (IOException e) {
                throw new IllegalStateException("Could not save world-load timing data", e);
            }
            rows.clear();
        }
    }
}

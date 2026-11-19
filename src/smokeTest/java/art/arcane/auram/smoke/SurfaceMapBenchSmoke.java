package art.arcane.auram.smoke;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Requests matching cold chunks through the vanilla ticket pipeline in two disposable worlds. */
@Mod.EventBusSubscriber(modid = "auram_patch_smoke")
public final class SurfaceMapBenchSmoke {
    private static final boolean ENABLED = "surfacemap-bench".equals(System.getProperty("auram.smoke"));
    private static final int REQUESTS = 64;
    private static final int MAX_PENDING = 12;
    private static final List<ChunkPos> pending = new ArrayList<>();
    private static final List<String> samples = new ArrayList<>();
    private static ServerLevel level;
    private static long tickStart;
    private static long start;
    private static int warmup = 100;
    private static int issued;
    private static int completed;

    private SurfaceMapBenchSmoke() {}

    @SubscribeEvent
    public static void start(ServerStartedEvent event) {
        if (ENABLED) {
            level = event.getServer().overworld();
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) throws IOException {
        if (level == null || !ENABLED) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            if (warmup == 0) {
                tickStart = System.nanoTime();
                if (start == 0) {
                    start = tickStart;
                }
            }
            return;
        }
        if (warmup > 0) {
            warmup--;
            return;
        }

        Iterator<ChunkPos> jobs = pending.iterator();
        while (jobs.hasNext()) {
            ChunkPos pos = jobs.next();
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) != null) {
                level.setChunkForced(pos.x, pos.z, false);
                jobs.remove();
                completed++;
            }
        }
        if (issued < REQUESTS && pending.size() < MAX_PENDING) {
            var pos = new ChunkPos(88 + issued % 8, 88 + issued / 8);
            level.setChunkForced(pos.x, pos.z, true);
            pending.add(pos);
            issued++;
        }
        long now = System.nanoTime();
        samples.add((now - start) + "," + (now - tickStart) + "," + issued + "," + completed);
        if (completed == REQUESTS) {
            finish(event.getServer());
        }
    }

    private static void finish(MinecraftServer server) throws IOException {
        samples.add(0, "elapsed_ns,tick_ns,requested,ready");
        Files.write(Path.of("surface-map-bench.csv"), samples);
        level = null;
        server.halt(false);
    }
}

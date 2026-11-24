package art.arcane.auram.smoke;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/** Requests matching cold chunks through the vanilla ticket pipeline in two disposable worlds. */
@Mod.EventBusSubscriber(modid = "auram_patch_smoke")
public final class SurfaceMapBenchSmoke {
    public static final boolean ENABLED = "surfacemap-bench".equals(System.getProperty("auram.smoke"));
    public static final boolean WORLDGEN = "worldgen-bench".equals(System.getProperty("auram.smoke"));
    private static final int REQUESTS = Integer.getInteger("auram.smoke.chunkRequests", 48);
    private static final int MAX_PENDING = Integer.getInteger("auram.smoke.pendingChunks", 12);
    private static final int ORIGIN_X = Integer.getInteger("auram.smoke.chunkOriginX", 88);
    private static final int ORIGIN_Z = Integer.getInteger("auram.smoke.chunkOriginZ", 88);
    private static final TicketType<ChunkPos> BENCH_TICKET = TicketType.create("auram_surface_bench",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final LongAdder mainCaptures = new LongAdder();
    private static final LongAdder mainCaptureNs = new LongAdder();
    private static final LongAccumulator maxMainCaptureNs = new LongAccumulator(Long::max, 0L);
    private static final LongAdder workerCaptures = new LongAdder();
    private static final LongAdder workerCaptureNs = new LongAdder();
    private static final LongAccumulator maxWorkerCaptureNs = new LongAccumulator(Long::max, 0L);
    private record Request(ChunkPos position, long started) {}

    private static final List<Request> pending = new ArrayList<>();
    private static final List<String> completedRequests = new ArrayList<>();
    private static final List<String> samples = new ArrayList<>();
    private static ServerLevel level;
    private static long tickStart;
    private static long start;
    private static long levelStarted;
    private static long coldStartMillis;
    private static long levelToSpawn;
    private static long spawnStarted;
    private static long spawnElapsed;
    private static int warmup = 100;
    private static int issued;
    private static int completed;

    private SurfaceMapBenchSmoke() {}

    @SubscribeEvent
    public static void start(ServerStartedEvent event) {
        if (ENABLED || WORLDGEN) {
            ResourceLocation dimension = new ResourceLocation(System.getProperty("auram.smoke.dimension", "minecraft:overworld"));
            level = event.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null) throw new IllegalArgumentException("Unknown benchmark dimension " + dimension);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) throws IOException {
        if (level == null || !(ENABLED || WORLDGEN)) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            if (warmup == 0) {
                tickStart = System.nanoTime();
                if (start == 0) {
                    start = tickStart;
                    if (WORLDGEN) {
                        coldStartMillis = System.currentTimeMillis();
                        AquiferReuseProbe.beginColdRun();
                        WorldgenTargets.beginCold();
                    }
                }
            }
            return;
        }
        if (warmup > 0) {
            warmup--;
            return;
        }

        Iterator<Request> jobs = pending.iterator();
        while (jobs.hasNext()) {
            Request request = jobs.next();
            ChunkPos pos = request.position();
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) != null) {
                level.getChunkSource().removeRegionTicket(BENCH_TICKET, pos, 0, pos);
                jobs.remove();
                completed++;
                if (WORLDGEN) {
                    completedRequests.add(pos.x + "," + pos.z + "," + (System.nanoTime() - request.started()));
                }
            }
        }
        if (issued < REQUESTS && pending.size() < MAX_PENDING) {
            var pos = new ChunkPos(ORIGIN_X + issued % 8, ORIGIN_Z + issued / 8);
            level.getChunkSource().addRegionTicket(BENCH_TICKET, pos, 0, pos);
            pending.add(new Request(pos, System.nanoTime()));
            issued++;
        }
        long now = System.nanoTime();
        samples.add((now - start) + "," + (now - tickStart) + "," + issued + "," + completed);
        if (samples.size() % 200 == 0) {
            System.out.println("Surface-map benchmark ready " + completed + "/" + REQUESTS);
        }
        if (completed == REQUESTS) {
            finish(event.getServer());
        }
    }

    public static void capture(long nanos, boolean mainThread) {
        if (mainThread) {
            mainCaptures.increment();
            mainCaptureNs.add(nanos);
            maxMainCaptureNs.accumulate(nanos);
        } else {
            workerCaptures.increment();
            workerCaptureNs.add(nanos);
            maxWorkerCaptureNs.accumulate(nanos);
        }
    }

    public static void levelStarted() {
        if (WORLDGEN) levelStarted = System.nanoTime();
    }

    public static void spawnStarted() {
        if (WORLDGEN) spawnStarted = System.nanoTime();
    }

    public static void spawnFinished() {
        if (WORLDGEN) {
            long now = System.nanoTime();
            spawnElapsed = now - spawnStarted;
            levelToSpawn = now - levelStarted;
        }
    }

    private static void finish(MinecraftServer server) throws IOException {
        if (WORLDGEN) {
            Files.writeString(Path.of("worldgen-cold-window.csv"), "start_epoch_ms,end_epoch_ms\n"
                    + coldStartMillis + "," + System.currentTimeMillis() + "\n");
            completedRequests.add(0, "chunk_x,chunk_z,ready_ns");
            Files.write(Path.of("worldgen-chunks.csv"), completedRequests);
            AquiferReuseProbe.write(Path.of("worldgen-stage-cpu.csv"));
            WorldgenTargets.write();
            Files.writeString(Path.of("worldgen-spawn-ns.txt"), Long.toString(spawnElapsed));
            Files.writeString(Path.of("worldgen-level-ready-ns.txt"), Long.toString(levelToSpawn));
        }
        samples.add(0, "elapsed_ns,tick_ns,requested,ready");
        Files.write(Path.of("surface-map-bench.csv"), samples);
        Files.writeString(Path.of("surface-map-capture.csv"), "thread,calls,total_ns,max_ns\n"
                + "main," + mainCaptures.sum() + "," + mainCaptureNs.sum() + "," + maxMainCaptureNs.get() + "\n"
                + "worker," + workerCaptures.sum() + "," + workerCaptureNs.sum() + "," + maxWorkerCaptureNs.get() + "\n");
        level = null;
        server.halt(false);
    }
}

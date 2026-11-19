package art.arcane.auram.smoke;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Collect per-tick timings, real DH tickets, and foreground chunk completion on a disposable server. */
@Mod.EventBusSubscriber(modid = "auram_patch_smoke")
public final class DistantHorizonsSmoke {
    public static final boolean SERVER = "dhpriority-server".equals(System.getProperty("auram.smoke"));
    public static final boolean CLIENT = "dhpriority-client".equals(System.getProperty("auram.smoke"));
    private static final boolean DEFER_DH = Boolean.getBoolean("auram.dh.defer");
    static final int FLIGHT_CLEARANCE = 16;
    private static final int FLIGHT_BLOCKS = Integer.getInteger("auram.dh.flightBlocks", -1);
    private static final Set<Queue> QUEUES = ConcurrentHashMap.newKeySet();
    private static final LongOpenHashSet ACTIVE = new LongOpenHashSet();
    private static final Long2ObjectOpenHashMap<Foreground> FOREGROUND = new Long2ObjectOpenHashMap<>();
    private static final List<String> TICKS = new ArrayList<>();
    private static final AtomicLong DEFERRED = new AtomicLong();
    private static volatile boolean foregroundPending;
    private static long tickStart;
    private static long previousTick;
    private static long started;
    private static double destinationX;
    private static boolean wroteArrival;
    private static long ticketAdds;
    private static long ticketRemovals;

    public interface Queue {
        int getWaitingTaskCount();
        int getInProgressTaskCount();
    }

    public static void observeQueue(Queue queue) {
        QUEUES.add(queue);
    }

    public static boolean deferBackgroundBatch() {
        if (!SERVER || !DEFER_DH || !foregroundPending) return false;
        recordDeferral();
        return true;
    }

    public static void recordDeferral() {
        DEFERRED.incrementAndGet();
    }

    public static void addTicket(ChunkPos pos) {
        ticketAdds++;
        ACTIVE.add(pos.toLong());
    }

    public static void removeTicket(ChunkPos pos) {
        ticketRemovals++;
        ACTIVE.remove(pos.toLong());
    }

    public static void delivered(ServerPlayer player, ChunkPos pos) {
        if (!"AuramQA".equals(player.getGameProfile().getName())) return;
        long now = System.nanoTime();
        var chunk = FOREGROUND.computeIfAbsent(pos.toLong(), key -> new Foreground());
        if (chunk.full == 0) chunk.full = now;
        if (chunk.sent == 0) chunk.sent = now;
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (SERVER) event.getServer().getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, event.getServer());
    }

    @SubscribeEvent
    public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (!SERVER || !(event.getEntity() instanceof ServerPlayer player)
                || !"AuramQA".equals(player.getGameProfile().getName())) return;
        // Keep the same horizontal spawn and avoid testing collisions with mountaintops.
        player.connection.teleport(player.getX(), player.serverLevel().getMaxBuildHeight() + FLIGHT_CLEARANCE,
                player.getZ(), -90, 0);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tickStart(TickEvent.ServerTickEvent event) {
        if (SERVER && event.phase == TickEvent.Phase.START) tickStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tickEnd(TickEvent.ServerTickEvent event) {
        if (!SERVER || event.phase != TickEvent.Phase.END) return;
        long now = System.nanoTime();
        var server = event.getServer();
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            if (started != 0) {
                save();
                server.halt(false);
                started = 0;
            }
            return;
        }
        var player = players.get(0);
        var center = player.chunkPosition();
        if (started == 0) {
            started = now;
            destinationX = player.getX() + FLIGHT_BLOCKS;
        }
        var level = player.serverLevel();
        var cache = level.getChunkSource();
        long elapsed = now - started;
        int missing = 0;
        for (int x = center.x - 2; x <= center.x + 2; x++) {
            for (int z = center.z - 2; z <= center.z + 2; z++) {
                long key = ChunkPos.asLong(x, z);
                var chunk = FOREGROUND.computeIfAbsent(key, ignored -> new Foreground());
                if (chunk.requested == 0) chunk.requested = now;
                if (chunk.full == 0 && cache.getChunkNow(x, z) != null) chunk.full = now;
                if (chunk.full == 0) missing++;
            }
        }
        foregroundPending = missing != 0;
        int waiting = 0;
        int running = 0;
        for (Queue queue : QUEUES) {
            waiting += queue.getWaitingTaskCount();
            running += queue.getInProgressTaskCount();
        }
        TICKS.add(csv(elapsed, now - tickStart, previousTick == 0 ? 0 : now - previousTick,
                center.x, center.z, missing, ticketAdds, ticketRemovals,
                ACTIVE.size(), waiting, running, DEFERRED.get()));
        previousTick = now;
        if (FLIGHT_BLOCKS >= 0 && !wroteArrival && player.getX() >= destinationX - 1.0E-6) {
            try {
                Files.writeString(Path.of("dh-route-reached"), player.getX() + "," + player.getZ() + "\n");
                wroteArrival = true;
            } catch (IOException error) {
                throw new IllegalStateException(error);
            }
        }
    }

    private static void save() {
        try {
            TICKS.add(0, "elapsed_ns,tick_ns,interval_ns,center_x,center_z,missing_full,ticket_adds,ticket_removals,active,waiting,running,deferred");
            List<String> chunks = new ArrayList<>();
            chunks.add("x,z,requested_ns,full_ns,sent_ns");
            for (var entry : FOREGROUND.long2ObjectEntrySet()) {
                var chunk = entry.getValue();
                if (chunk.requested == 0) continue;
                long key = entry.getLongKey();
                chunks.add(csv(ChunkPos.getX(key), ChunkPos.getZ(key), chunk.requested - started,
                        observedLatency(chunk.requested, chunk.full), observedLatency(chunk.requested, chunk.sent)));
            }
            Files.write(Path.of("dh-server-ticks.csv"), TICKS);
            Files.write(Path.of("dh-server-chunks.csv"), chunks);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long observedLatency(long start, long observed) {
        return observed == 0 ? -1 : Math.max(0, observed - start);
    }

    static String csv(long... values) {
        var line = new StringBuilder();
        for (long value : values) {
            if (!line.isEmpty()) line.append(',');
            line.append(value);
        }
        return line.toString();
    }

    private static final class Foreground {
        long requested;
        long full;
        long sent;
    }
}

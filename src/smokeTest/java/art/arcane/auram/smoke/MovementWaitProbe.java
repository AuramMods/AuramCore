package art.arcane.auram.smoke;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Measure synchronous chunk waits attributable to movement during a connected flight replay. */
@Mod.EventBusSubscriber(modid = "auram_patch_smoke")
public final class MovementWaitProbe {
    public static final boolean ENABLED = DistantHorizonsSmoke.SERVER && Boolean.getBoolean("auram.movement.profile");
    private static final ChunkPos DELAY_CHUNK = delayChunkFromProperty();
    private static final CompletableFuture<Void> RELEASE_CHUNK = new CompletableFuture<>();
    private static boolean releaseScheduled;
    private static final ThreadLocal<PacketSample> CURRENT = new ThreadLocal<>();
    private static final List<String> SAMPLES = new ArrayList<>();

    public static boolean tracks(ServerPlayer player) {
        return ENABLED && player.serverLevel().getServer().isSameThread()
                && "AuramQA".equals(player.getGameProfile().getName()) && CURRENT.get() == null;
    }

    public static void begin(ServerPlayer player, ServerboundMovePlayerPacket packet) {
        double x = packet.getX(player.getX());
        double z = packet.getZ(player.getZ());
        CURRENT.set(new PacketSample(System.nanoTime(), player.getX(), player.getZ(), x, z));
        if (DELAY_CHUNK != null && !releaseScheduled && x >= DELAY_CHUNK.getMinBlockX() - 4
                && z >= DELAY_CHUNK.getMinBlockZ() && z <= DELAY_CHUNK.getMaxBlockZ()) {
            releaseScheduled = true;
            CompletableFuture.delayedExecutor(1500, TimeUnit.MILLISECONDS).execute(() -> RELEASE_CHUNK.complete(null));
        }
    }

    private static ChunkPos delayChunkFromProperty() {
        String value = System.getProperty("auram.movement.delayChunk", "");
        if (!ENABLED || value.isEmpty()) return null;
        String[] pair = value.split(",");
        return new ChunkPos(Integer.parseInt(pair[0]), Integer.parseInt(pair[1]));
    }

    public static boolean delayChunk(ChunkPos pos) {
        return pos.equals(DELAY_CHUNK) && !RELEASE_CHUNK.isDone();
    }

    public static <T> CompletableFuture<T> holdResult(CompletableFuture<T> result) {
        return result.thenCombine(RELEASE_CHUNK, (value, ignored) -> value);
    }

    public static boolean observing() {
        return ENABLED && CURRENT.get() != null;
    }

    public static void chunkWait(long elapsed, boolean create) {
        var sample = CURRENT.get();
        if (create) {
            sample.forcedWaits++;
            sample.forcedNanos += elapsed;
        } else {
            sample.existingWaits++;
            sample.existingNanos += elapsed;
        }
    }

    public static void end(ServerPlayer player) {
        var sample = CURRENT.get();
        long elapsed = System.nanoTime() - sample.started;
        CURRENT.remove();
        SAMPLES.add(String.join(",", Long.toString(elapsed),
                Double.toString(sample.startX), Double.toString(sample.startZ),
                Double.toString(sample.targetX), Double.toString(sample.targetZ),
                Double.toString(player.getX()), Double.toString(player.getZ()),
                Long.toString(sample.forcedWaits), Long.toString(sample.forcedNanos),
                Long.toString(sample.existingWaits), Long.toString(sample.existingNanos)));
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) throws IOException {
        if (!ENABLED) return;
        SAMPLES.add(0, "packet_ns,from_x,from_z,target_x,target_z,applied_x,applied_z,forced_full_waits,forced_full_ns,existing_full_waits,existing_full_ns");
        Files.write(Path.of("movement-waits.csv"), SAMPLES);
    }

    private static final class PacketSample {
        private final long started;
        private final double startX;
        private final double startZ;
        private final double targetX;
        private final double targetZ;
        private long forcedWaits;
        private long forcedNanos;
        private long existingWaits;
        private long existingNanos;

        private PacketSample(long started, double startX, double startZ, double targetX, double targetZ) {
            this.started = started;
            this.startX = startX;
            this.startZ = startZ;
            this.targetX = targetX;
            this.targetZ = targetZ;
        }
    }
}

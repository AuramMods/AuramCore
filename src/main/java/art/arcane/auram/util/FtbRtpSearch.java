package art.arcane.auram.util;

import art.arcane.auram.Auram;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Continue the stock FTB search over server ticks while chunk generation runs normally. */
@Mod.EventBusSubscriber(modid = Auram.MODID)
public final class FtbRtpSearch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TicketType<UUID> TICKET = TicketType.create("auram_ftb_rtp", Comparator.<UUID>naturalOrder());
    private static final long LOAD_TIMEOUT = TimeUnit.MINUTES.toNanos(2);
    private static final int SPIRAL_RADIUS = 16;
    private static final ResourceLocation IGNORE = ResourceLocation.tryBuild("ftbessentials", "ignore_rtp");
    private static final TagKey<Biome> IGNORE_BIOMES = TagKey.create(Registries.BIOME, IGNORE);
    private static final TagKey<Block> IGNORE_BLOCKS = TagKey.create(Registries.BLOCK, IGNORE);
    private static final Map<ServerPlayer, FtbRtpSearch> ACTIVE = new IdentityHashMap<>();
    private static final ThreadLocal<Boolean> CHECKING_BIOME = new ThreadLocal<>();
    private static final PendingBiome PENDING_BIOME = new PendingBiome();

    private final Object teleporter;
    private final ServerPlayer player;
    private final ServerLevel level;
    private final FtbRtpBindings ftb;
    private final List<ChunkPos> tickets = new ArrayList<>(10);
    private BlockPos candidate;
    private BlockPos spiralCenter;
    private BlockPos destination;
    private int attempt;
    private boolean exhausted;
    private long waitStarted;

    private FtbRtpSearch(Object teleporter, ServerPlayer player) {
        this.teleporter = teleporter;
        this.player = player;
        level = player.serverLevel();
        ftb = FtbRtpBindings.INSTANCE;
    }

    public static Function<ServerPlayer, ?> markRtp(Function<ServerPlayer, ?> finder) {
        return FtbRtpBindings.INSTANCE == null ? finder : new RtpPosition(finder);
    }

    public static boolean isRtp(Function<?, ?> finder) {
        return finder instanceof RtpPosition;
    }

    public static boolean isActive(ServerPlayer player) {
        return ACTIVE.containsKey(player);
    }

    public static Object duplicateResult() {
        return FtbRtpBindings.INSTANCE.failed(Component.literal("Already looking for a random location!"));
    }

    public static Object begin(Object teleporter, ServerPlayer player) {
        var search = new FtbRtpSearch(teleporter, player);
        ACTIVE.put(player, search);
        player.displayClientMessage(Component.literal("Looking for random location..."), false);
        return search.ftb.success;
    }

    /** Reuse the vanilla cached lookup and unwind just before a pending biome future could wait. */
    public static void yieldPendingBiome(BooleanSupplier ready) {
        if (CHECKING_BIOME.get() != null && !ready.getAsBoolean()) throw PENDING_BIOME;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        for (FtbRtpSearch search : List.copyOf(ACTIVE.values())) {
            if (search.level.getServer() != event.getServer() || ACTIVE.get(search.player) != search) continue;
            if (!search.validPlayer()) {
                search.cancel(false);
                continue;
            }
            try {
                search.step();
            } catch (RuntimeException error) {
                search.cancel(false);
                search.player.displayClientMessage(Component.literal("Could not complete random teleport.")
                        .withStyle(ChatFormatting.RED), false);
                LOGGER.error("FTB random teleport did not complete", error);
            }
        }
    }

    @SubscribeEvent
    public static void disconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FtbRtpSearch search = ACTIVE.get(player);
            if (search != null) search.cancel(false);
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        for (FtbRtpSearch search : List.copyOf(ACTIVE.values())) {
            if (search.level.getServer() == event.getServer()) search.cancel(false);
        }
    }

    public static void interrupt(ServerPlayer player) {
        FtbRtpSearch search = ACTIVE.get(player);
        if (search != null) search.cancel(true);
    }

    private boolean validPlayer() {
        return !player.isRemoved() && player.serverLevel() == level
                && level.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private void step() {
        if (candidate == null && !exhausted) {
            if (attempt >= ftb.maxTries()) {
                exhausted = true;
                waitStarted = System.nanoTime();
                player.displayClientMessage(Component.literal("Could not find a valid location to teleport to!")
                        .withStyle(ChatFormatting.RED), false);
            } else {
                int min = ftb.minDistance();
                double distance = min + level.random.nextDouble() * (ftb.maxDistance() - min);
                double angle = level.random.nextDouble() * Math.PI * 2.0;
                candidate = new BlockPos(Mth.floor(Math.cos(angle) * distance), 256,
                        Mth.floor(Math.sin(angle) * distance));
                waitStarted = System.nanoTime();
            }
        }
        if (System.nanoTime() - waitStarted >= LOAD_TIMEOUT) {
            cancel(false);
            player.displayClientMessage(Component.literal("Timed out loading a random teleport destination.")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        if (exhausted) {
            // Retain FTB's fallback to the current position, including its history handling.
            var current = player.chunkPosition();
            if (!tickets.contains(current)) {
                releaseTickets();
                hold(current);
            }
            if (ready()) finish(null);
            return;
        }
        if (tickets.isEmpty()) {
            try {
                if (!level.getWorldBorder().isWithinBounds(candidate) || isExcludedBiome()
                        || !ftb.permits(level, player, candidate, attempt)) {
                    reject();
                    return;
                }
            } catch (PendingBiome pending) {
                return;
            }
            if (!validPlayer() || ACTIVE.get(player) != this) {
                cancel(false);
                return;
            }
            hold(new ChunkPos(candidate));
        }
        if (!ready()) return;
        if (destination != null) {
            finishSelected();
            return;
        }
        if (spiralCenter == null) {
            var height = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate);
            if (height.getY() <= 0) {
                reject();
                return;
            }
            if (height.getY() < level.getLogicalHeight()) {
                select(height);
                return;
            }
            spiralCenter = new BlockPos(height.getX(), level.getSeaLevel(), height.getZ());
            for (int x = (height.getX() - SPIRAL_RADIUS) >> 4; x <= (height.getX() + SPIRAL_RADIUS) >> 4; x++) {
                for (int z = (height.getZ() - SPIRAL_RADIUS) >> 4; z <= (height.getZ() + SPIRAL_RADIUS) >> 4; z++) {
                    var chunk = new ChunkPos(x, z);
                    if (!tickets.contains(chunk)) hold(chunk);
                }
            }
            waitStarted = System.nanoTime();
            if (!ready()) return;
        }
        BlockPos found = null;
        for (BlockPos pos : BlockPos.spiralAround(spiralCenter, SPIRAL_RADIUS, Direction.EAST, Direction.SOUTH)) {
            var state = level.getBlockState(pos);
            if (state.blocksMotion() && !state.is(IGNORE_BLOCKS)
                    && level.isEmptyBlock(pos.above()) && level.isEmptyBlock(pos.above(2)) && level.isEmptyBlock(pos.above(3))) {
                // FTB keeps the iterator's mutable cursor until the spiral ends.
                found = pos;
            }
        }
        if (found == null) reject();
        else select(found);
    }

    private boolean isExcludedBiome() {
        CHECKING_BIOME.set(Boolean.TRUE);
        try {
            return level.getBiome(candidate).is(IGNORE_BIOMES);
        } finally {
            CHECKING_BIOME.remove();
        }
    }

    private void hold(ChunkPos pos) {
        tickets.add(pos);
        level.getChunkSource().addRegionTicket(TICKET, pos, 0, player.getUUID());
    }

    private boolean ready() {
        for (ChunkPos pos : tickets) {
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) == null) return false;
        }
        return true;
    }

    private void releaseTickets() {
        for (ChunkPos pos : tickets) level.getChunkSource().removeRegionTicket(TICKET, pos, 0, player.getUUID());
        tickets.clear();
    }

    private void reject() {
        releaseTickets();
        candidate = null;
        spiralCenter = null;
        attempt++;
    }

    private void select(BlockPos pos) {
        destination = pos.above();
        ChunkPos arrival = new ChunkPos(destination);
        if (!tickets.contains(arrival)) {
            hold(arrival);
            waitStarted = System.nanoTime();
        }
        if (ready()) finishSelected();
    }

    private void finishSelected() {
        player.displayClientMessage(Component.literal(String.format(
                "Found good location after %d " + (attempt == 1 ? "attempt" : "attempts") + " @ [x %d, z %d]",
                attempt, destination.getX(), destination.getZ())), false);
        finish(destination);
    }

    private void finish(BlockPos pos) {
        try {
            ftb.finish(teleporter, player, pos);
        } finally {
            cancel(false);
        }
    }

    private void cancel(boolean report) {
        ACTIVE.remove(player, this);
        releaseTickets();
        if (report) player.displayClientMessage(Component.literal("Teleportation interrupted!")
                .withStyle(ChatFormatting.RED), true);
    }

    private record RtpPosition(Function<ServerPlayer, ?> fallback) implements Function<ServerPlayer, Object> {
        @Override public Object apply(ServerPlayer player) { return fallback.apply(player); }
    }

    public record ReadyPosition(Object position) implements Function<ServerPlayer, Object> {
        @Override public Object apply(ServerPlayer player) { return position; }
    }

    private static final class PendingBiome extends RuntimeException {
        private PendingBiome() { super(null, null, false, false); }
    }
}

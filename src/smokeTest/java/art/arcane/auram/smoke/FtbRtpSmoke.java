package art.arcane.auram.smoke;

import art.arcane.auram.smoke.mixin.PlayerListAccess;
import art.arcane.auram.util.FtbRtpSearch;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Exercise the registered FTB command with a second, independently ticking player. */
@Mod.EventBusSubscriber(modid = "auram_patch_smoke")
public final class FtbRtpSmoke {
    private static final String FTB = "dev.ftb.mods.ftbessentials";
    private static final boolean ENABLED = "ftbrtp".equals(System.getProperty("auram.smoke"));
    private static final boolean NETHER = Boolean.getBoolean("auram.smoke.rtpNether");
    private static final boolean LIFECYCLE = Boolean.getBoolean("auram.smoke.rtpLifecycle");
    private static final boolean PENDING_BIOME = Boolean.getBoolean("auram.smoke.rtpPendingBiome");
    private static FtbRtpSmoke active;

    private final MinecraftServer server;
    private final ServerLevel level;
    private final ProbePlayer requester;
    private final ProbePlayer observer;
    private final List<Attempt> attempts = new ArrayList<>();
    private final List<Long> tickNanos = new ArrayList<>();
    private long tickStart;
    private int tick;
    private int observerAtStart;
    private int observerAtReturn;
    private int observerAtCompletion;
    private long commandNanos;
    private long completionNanos;
    private long commandStarted;
    private BlockPos origin;
    private boolean checkedSuccess;
    private TicketChecks ticketChecks;
    private final CompletableFuture<Void> biomeGate = new CompletableFuture<>();
    private ChunkPos biomeChunk;
    private int observerAtBiomeWait;
    private int pendingBiomeReads;

    private FtbRtpSmoke(MinecraftServer server) throws ReflectiveOperationException {
        this.server = server;
        level = NETHER ? server.getLevel(Level.NETHER) : server.overworld();
        requester = NETHER ? player("AuramRtpProbe", 1001, level, 160.5, 128) :
                player("AuramRtpProbe", 1001, level, 8.5, 9);
        observer = player("AuramRtpObserver", 1002, server.overworld(), 10.5, 9);
        var config = Class.forName(FTB + ".config.FTBEConfig");
        setDistance(NETHER ? 0 : 4096);
        setInt(config, "RTP_MAX_TRIES", 4);
        var eventType = Class.forName("dev.architectury.event.Event");
        var resultType = Class.forName("dev.architectury.event.EventResult");
        Object pass = resultType.getMethod("pass").invoke(null);
        Object veto = resultType.getMethod("interruptFalse").invoke(null);
        var callbackType = Class.forName(FTB + ".FTBEssentialsEvents$RTP");
        var event = Class.forName(FTB + ".FTBEssentialsEvents").getField("RTP_EVENT").get(null);
        Object listener = Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[]{callbackType},
                (proxy, method, args) -> {
                    ServerLevel world = (ServerLevel) args[0];
                    BlockPos pos = (BlockPos) args[2];
                    int number = (Integer) args[3];
                    if (ticketChecks != null && args[1] == ticketChecks.player) return pass;
                    boolean approved = args[1] == requester && number == acceptedAttempt();
                    boolean full = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
                    attempts.add(new Attempt(number, pos.getX(), pos.getZ(), approved, full));
                    return approved ? pass : veto;
                });
        eventType.getMethod("register", Object.class).invoke(event, listener);
    }

    public static <T> CompletableFuture<T> holdFirstCandidateBiome(ServerChunkCache source, int x, int z,
                                                                   CompletableFuture<T> future) {
        var smoke = active;
        if (!PENDING_BIOME || smoke == null || smoke.biomeGate.isDone() || !FtbRtpSearch.isActive(smoke.requester)
                || source != smoke.level.getChunkSource() || Math.hypot(x, z) < 240) return future;
        var chunk = new ChunkPos(x, z);
        if (smoke.biomeChunk == null) {
            smoke.biomeChunk = chunk;
            smoke.observerAtBiomeWait = smoke.observer.tickCount;
        }
        check(smoke.biomeChunk.equals(chunk), "the same biome column is retried while pending");
        smoke.pendingBiomeReads++;
        return future.thenCombine(smoke.biomeGate, (result, released) -> result);
    }

    @SubscribeEvent
    public static void prepareNether(ChunkEvent.Load event) {
        if (!ENABLED || !NETHER || !(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.NETHER || !(event.getChunk() instanceof LevelChunk chunk)
                || chunk.getPos().x != 0 || chunk.getPos().z != 0) return;
        // Give the stock ceiling scan one guaranteed usable floor without loading it in advance.
        var floor = new BlockPos(0, level.getSeaLevel(), 0);
        chunk.setBlockState(floor, Blocks.NETHERRACK.defaultBlockState(), false);
        for (int y = 1; y <= 3; y++) chunk.setBlockState(floor.above(y), Blocks.AIR.defaultBlockState(), false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void startTick(TickEvent.ServerTickEvent event) {
        if (ENABLED && active != null && event.phase == TickEvent.Phase.START) active.tickStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void endTick(TickEvent.ServerTickEvent event) throws Exception {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        if (active == null) {
            active = new FtbRtpSmoke(event.getServer());
            return;
        }
        active.step();
    }

    private void step() throws Exception {
        if (++tick >= 600) throw new AssertionError("FTB smoke did not resolve within 600 server ticks");
        if (ticketChecks != null) {
            if (ticketChecks.step()) complete();
            return;
        }
        if (tick == 25) {
            origin = requester.blockPosition();
            observerAtStart = observer.tickCount;
            commandStarted = System.nanoTime();
            runRtp(requester);
            commandNanos = System.nanoTime() - commandStarted;
            observerAtReturn = observer.tickCount;
        }
        if (tickStart != 0) tickNanos.add(System.nanoTime() - tickStart);
        if (biomeChunk != null && !biomeGate.isDone() && observer.tickCount - observerAtBiomeWait >= 5) {
            check(attempts.isEmpty() && pendingBiomeReads > 1, "biome wait suspends before any RTP listener runs");
            biomeGate.complete(null);
        }
        if (!checkedSuccess && requester.teleport != null) {
            checkedSuccess = true;
            completionNanos = System.nanoTime() - commandStarted;
            observerAtCompletion = observer.tickCount;
            check(!requester.blockPosition().equals(origin), "the command changed the player's location");
            check(attempts.size() == acceptedAttempt() + 1, "the command tried each candidate once");
            var accepted = attempts.get(acceptedAttempt());
            check(accepted.approved() && !accepted.fullAtEvent(), "the accepted destination started unloaded");
            BlockPos height = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(accepted.x(), 256, accepted.z()));
            if (NETHER) {
                check(height.getY() >= level.getLogicalHeight(), "the roof forced a Nether headroom scan");
                check(Math.abs(requester.getBlockX()) <= 17 && Math.abs(requester.getBlockZ()) <= 17,
                        "the Nether arrival used the scan neighborhood");
                check(Math.abs(requester.teleport.y - (level.getSeaLevel() + 1.1)) < 0.00001,
                        "the Nether arrival preserved the exact sea-level offset");
            } else {
                check(requester.teleport.equals(new Vec3(accepted.x() + 0.5, height.above().getY() + 0.1, accepted.z() + 0.5)),
                        "FTB applies its exact centering and heightmap offset");
            }
            check(history(requester) == 1, "successful RTP saves one position for /back");
            int eventsBeforeCooldown = attempts.size();
            runRtp(requester);
            check(attempts.size() == eventsBeforeCooldown, "cooldown stops a repeated command before candidate events");

            setDistance(4096);
            BlockPos beforeVeto = observer.blockPosition();
            runRtp(observer);
            check(observer.blockPosition().equals(beforeVeto), "all-veto search keeps the player at the origin");
        }
        if (checkedSuccess && tick >= 100 && observer.messages.stream().anyMatch(
                text -> text.contains("Could not find a valid location"))) {
            check(observer.tickCount > observerAtStart, "the observer received real player ticks");
            var expectedAttempts = NETHER ? List.of(0, 0, 1, 2, 3) : List.of(0, 1, 2, 0, 1, 2, 3);
            check(attempts.stream().map(Attempt::index).toList().equals(expectedAttempts),
                    "candidate events are called in attempt order, once per candidate");
            check(attempts.subList(acceptedAttempt() + 1, attempts.size()).stream().noneMatch(Attempt::fullAtEvent),
                    "vetoed fixture candidates start unloaded");
            var report = Map.of("command_nanos", commandNanos, "completion_nanos", completionNanos,
                    "observer_ticks_in_command", observerAtReturn - observerAtStart,
                    "observer_ticks_to_completion", observerAtCompletion - observerAtStart,
                    "observer_ticks_since_command", observer.tickCount - observerAtStart,
                    "destination", requester.teleport.toString(),
                    "attempts", attempts, "tick_nanos", tickNanos,
                    "requester_messages", requester.messages, "veto_messages", observer.messages);
            Files.writeString(Path.of("ftb-rtp-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            if (PENDING_BIOME) {
                check(biomeGate.isDone() && pendingBiomeReads > 1, "a pending BIOMES lookup was retried and released");
                Files.writeString(Path.of("ftb-rtp-biome-results.txt"), pendingBiomeReads + " reads for one pending biome column\n");
            }
            if (LIFECYCLE) ticketChecks = new TicketChecks();
            else complete();
        }
    }

    private void complete() throws Exception {
        Files.writeString(Path.of("auram-ftbrtp-pass.txt"), "PASS FTB command, candidate events, cooldown, history and all-veto search\n");
        server.halt(false);
    }

    private final class TicketChecks {
        private final ProbePlayer player = player("AuramRtpCancel", 1003, server.overworld(), 9.5, 9);
        private final List<String> results = new ArrayList<>();
        private int phase;

        private TicketChecks() throws ReflectiveOperationException {
            setTimer("cooldown", 0);
            setTimer("warmup", 3);
            runRtp(player);
        }

        private boolean step() throws Exception {
            switch (phase) {
                case 0 -> {
                    if (hasMessage("Teleporting in")) {
                        check(!FtbRtpSearch.isActive(player), "native warmup precedes the asynchronous search");
                        player.setPos(player.getX() + 0.75, player.getY(), player.getZ());
                        phase++;
                    }
                }
                case 1 -> {
                    if (hasMessage("Teleportation interrupted!")) {
                        verifyReleased("movement interrupts native warmup");
                        setTimer("warmup", 0);
                        startNext(8192);
                    }
                }
                case 2 -> {
                    if (tickets() != 0) {
                        Class.forName(FTB + ".util.WarmupCooldownTeleporter")
                                .getMethod("cancelWarmup", ServerPlayer.class).invoke(null, player);
                        check(hasMessage("Teleportation interrupted!"), "the cancellation reports to the requester");
                        verifyReleased("FTB cancelWarmup releases the pending FULL ticket");
                        startNext(12288);
                    }
                }
                case 3 -> {
                    if (tickets() != 0) {
                        var activeField = FtbRtpSearch.class.getDeclaredField("ACTIVE");
                        activeField.setAccessible(true);
                        var requests = (Map<?, ?>) activeField.get(null);
                        var waitStarted = FtbRtpSearch.class.getDeclaredField("waitStarted");
                        waitStarted.setAccessible(true);
                        waitStarted.setLong(requests.get(player), System.nanoTime() - TimeUnit.MINUTES.toNanos(3));
                        phase++;
                    }
                }
                case 4 -> {
                    if (hasMessage("Timed out loading")) {
                        verifyReleased("a timed-out FULL wait releases its ticket");
                        startNext(16384);
                    }
                }
                case 5 -> {
                    if (tickets() != 0) {
                        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                        verifyReleased("logout releases the pending FULL ticket");
                        Files.write(Path.of("ftb-rtp-ticket-results.txt"), results);
                        return true;
                    }
                }
                default -> throw new IllegalStateException("Unexpected ticket test phase " + phase);
            }
            return false;
        }

        private void startNext(int distance) throws ReflectiveOperationException {
            player.messages.clear();
            setDistance(distance);
            runRtp(player);
            phase++;
        }

        private boolean hasMessage(String text) {
            return player.messages.stream().anyMatch(message -> message.contains(text));
        }

        private void verifyReleased(String description) throws ReflectiveOperationException {
            check(tickets() == 0 && !FtbRtpSearch.isActive(player), description);
            check(player.teleport == null && history(player) == 0, "cancelled requests never arrive or record /back");
            results.add(description);
        }

        private long tickets() throws ReflectiveOperationException {
            var field = ObfuscationReflectionHelper.findField(DistanceManager.class, "f_140761_");
            var manager = player.serverLevel().getChunkSource().chunkMap.getDistanceManager();
            var all = (Map<?, ?>) field.get(manager);
            long count = 0;
            for (Object bucket : all.values()) {
                for (Object entry : (Iterable<?>) bucket) {
                    var ticket = (Ticket<?>) entry;
                    if (ticket.getType().toString().equals("auram_ftb_rtp")) count++;
                }
            }
            return count;
        }
    }

    private ProbePlayer player(String name, long id, ServerLevel level, double x, double y) {
        ProbePlayer player = new ProbePlayer(server, level, new GameProfile(new UUID(0, id), name));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) { }
        };
        player.setPos(x, y, 8.5);
        var playerList = (PlayerListAccess) server.getPlayerList();
        playerList.auramSmoke$players().add(player);
        playerList.auramSmoke$playersByUUID().put(player.getUUID(), player);
        level.addNewPlayer(player);
        return player;
    }

    private void runRtp(ProbePlayer player) {
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "rtp");
    }

    private static int history(ServerPlayer player) throws ReflectiveOperationException {
        var type = Class.forName(FTB + ".util.FTBEPlayerData");
        var value = (java.util.Optional<?>) type.getMethod("getOrCreate", net.minecraft.world.entity.player.Player.class)
                .invoke(null, player);
        return ((List<?>) type.getField("teleportHistory").get(value.orElseThrow())).size();
    }

    private static int acceptedAttempt() {
        return NETHER ? 0 : 2;
    }

    private static void setDistance(int blocks) throws ReflectiveOperationException {
        var config = Class.forName(FTB + ".config.FTBEConfig");
        setInt(config, "RTP_MIN_DISTANCE", blocks);
        setInt(config, "RTP_MAX_DISTANCE", blocks);
    }

    private static void setTimer(String name, int seconds) throws ReflectiveOperationException {
        var config = Class.forName(FTB + ".config.FTBEConfig").getField("RTP").get(null);
        var timer = config.getClass().getDeclaredField(name);
        timer.setAccessible(true);
        var permission = timer.get(config);
        setIntValue(permission.getClass().getField("value").get(permission), seconds);
    }

    private static void setInt(Class<?> config, String name, int value) throws ReflectiveOperationException {
        setIntValue(config.getField(name).get(null), value);
    }

    private static void setIntValue(Object entry, int value) throws ReflectiveOperationException {
        entry.getClass().getMethod("set", Integer.class).invoke(entry, value);
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    private record Attempt(int index, int x, int z, boolean approved, boolean fullAtEvent) { }

    private static final class ProbePlayer extends ServerPlayer {
        private final List<String> messages = new ArrayList<>();
        private Vec3 teleport;

        private ProbePlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
            super(server, level, profile);
        }

        @Override public void displayClientMessage(Component message, boolean overlay) {
            messages.add(message.getString());
        }

        @Override
        public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
            teleport = new Vec3(x, y, z);
            super.teleportTo(level, x, y, z, yaw, pitch);
        }
    }
}

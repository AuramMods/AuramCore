package art.arcane.auram.smoke;

import art.arcane.auram.smoke.mixin.PlayerListAccess;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Exercise the registered FTB command with a second, independently ticking player. */
@Mod.EventBusSubscriber(modid = "auram_patch_smoke")
public final class FtbRtpSmoke {
    private static final String FTB = "dev.ftb.mods.ftbessentials";
    private static final boolean ENABLED = "ftbrtp".equals(System.getProperty("auram.smoke"));
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

    private FtbRtpSmoke(MinecraftServer server) throws ReflectiveOperationException {
        this.server = server;
        level = server.overworld();
        requester = player("AuramRtpProbe", 1001, 8.5);
        observer = player("AuramRtpObserver", 1002, 10.5);
        var config = Class.forName(FTB + ".config.FTBEConfig");
        setInt(config, "RTP_MIN_DISTANCE", 4096);
        setInt(config, "RTP_MAX_DISTANCE", 4096);
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
                    boolean approved = args[1] == requester && number == 2;
                    boolean full = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
                    attempts.add(new Attempt(number, pos.getX(), pos.getZ(), approved, full));
                    return approved ? pass : veto;
                });
        eventType.getMethod("register", Object.class).invoke(event, listener);
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
        if (++tick == 25) {
            origin = requester.blockPosition();
            observerAtStart = observer.tickCount;
            commandStarted = System.nanoTime();
            runRtp(requester);
            commandNanos = System.nanoTime() - commandStarted;
            observerAtReturn = observer.tickCount;
        }
        if (tickStart != 0) tickNanos.add(System.nanoTime() - tickStart);
        if (!checkedSuccess && origin != null && !requester.blockPosition().equals(origin)) {
            checkedSuccess = true;
            completionNanos = System.nanoTime() - commandStarted;
            observerAtCompletion = observer.tickCount;
            check(attempts.size() == 3 && attempts.get(2).approved() && !attempts.get(2).fullAtEvent(),
                    "first two attempts vetoed and the accepted destination started unloaded");
            var accepted = attempts.get(2);
            check(requester.getBlockX() == accepted.x() && requester.getBlockZ() == accepted.z(),
                    "the command landed on the accepted candidate");
            check(history(requester) == 1, "successful RTP saves one position for /back");
            int eventsBeforeCooldown = attempts.size();
            runRtp(requester);
            check(attempts.size() == eventsBeforeCooldown, "cooldown stops a repeated command before candidate events");

            BlockPos beforeVeto = observer.blockPosition();
            runRtp(observer);
            check(observer.blockPosition().equals(beforeVeto), "all-veto search keeps the player at the origin");
        }
        if (checkedSuccess && tick >= 100 && observer.messages.stream().anyMatch(
                text -> text.contains("Could not find a valid location"))) {
            check(observer.tickCount > observerAtStart, "the observer received real player ticks");
            check(attempts.stream().map(Attempt::index).toList().equals(List.of(0, 1, 2, 0, 1, 2, 3)),
                    "candidate events are called in attempt order, once per candidate");
            check(attempts.subList(3, 7).stream().noneMatch(Attempt::fullAtEvent),
                    "vetoed fixture candidates start unloaded");
            var report = Map.of("command_nanos", commandNanos, "completion_nanos", completionNanos,
                    "observer_ticks_in_command", observerAtReturn - observerAtStart,
                    "observer_ticks_to_completion", observerAtCompletion - observerAtStart,
                    "observer_ticks_since_command", observer.tickCount - observerAtStart,
                    "destination", requester.blockPosition().toShortString(),
                    "attempts", attempts, "tick_nanos", tickNanos,
                    "requester_messages", requester.messages, "veto_messages", observer.messages);
            Files.writeString(Path.of("ftb-rtp-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            Files.writeString(Path.of("auram-ftbrtp-pass.txt"), "PASS FTB command, candidate events, cooldown, history and all-veto search\n");
            server.halt(false);
        } else if (tick >= 600) {
            throw new AssertionError("FTB smoke did not resolve within 600 server ticks");
        }
    }

    private ProbePlayer player(String name, long id, double x) {
        ProbePlayer player = new ProbePlayer(server, level, new GameProfile(new UUID(0, id), name));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) { }
        };
        player.setPos(x, 9, 8.5);
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

    private static void setInt(Class<?> config, String name, int value) throws ReflectiveOperationException {
        var entry = config.getField(name).get(null);
        entry.getClass().getMethod("set", Integer.class).invoke(entry, value);
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    private record Attempt(int index, int x, int z, boolean approved, boolean fullAtEvent) { }

    private static final class ProbePlayer extends ServerPlayer {
        private final List<String> messages = new ArrayList<>();

        private ProbePlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
            super(server, level, profile);
        }

        @Override public void displayClientMessage(Component message, boolean overlay) {
            messages.add(message.getString());
        }
    }
}

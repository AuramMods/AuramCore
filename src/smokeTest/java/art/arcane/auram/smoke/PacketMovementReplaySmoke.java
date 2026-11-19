package art.arcane.auram.smoke;

import art.arcane.auram.smoke.mixin.PlayerListAccess;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

@Mod.EventBusSubscriber(modid = "auram_patch_smoke")
public final class PacketMovementReplaySmoke {
    private static final String MODE = "movementpacketreplay";
    private static final boolean CANDIDATE = Boolean.getBoolean("auram.smoke.movementCandidate");
    private static final boolean WALKING = Boolean.getBoolean("auram.smoke.movementWalking");
    private static final int PACKET_COUNT = Integer.getInteger("auram.smoke.movementPackets", 2);
    private static PacketMovementReplaySmoke active;

    private final MinecraftServer server;
    private final ServerLevel level;
    private final ProbePlayer player;
    private final ProbePlayer observer;
    private final List<Map<String, Object>> trials = new ArrayList<>();
    private ChunkPos east;
    private Vec3 from;
    private int startedTick;
    private int observerAtSend;
    private long duration;

    private PacketMovementReplaySmoke(MinecraftServer server) {
        this.server = server;
        level = server.overworld();
        player = join("AuramMoveReplay", 1102);
        observer = join("AuramMoveWitness", 1103);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.ServerTickEvent event) throws Exception {
        if (!MODE.equals(System.getProperty("auram.smoke")) || event.phase != TickEvent.Phase.END) return;
        if (active == null) {
            active = new PacketMovementReplaySmoke(event.getServer());
            return;
        }
        active.step();
    }

    private void step() throws Exception {
        var source = level.getChunkSource();
        if (east == null) {
            var home = new ChunkPos(24 + trials.size() * 32, 24);
            east = new ChunkPos(home.x + 1, home.z);
            level.getChunk(home.x, home.z);
            var floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING, home.getMinBlockX() + 15,
                    home.getMinBlockZ() + 8);
            from = new Vec3(home.getMinBlockX() + 15.5, WALKING ? floor : 90, home.getMinBlockZ() + 8.5);
            player.setPos(from.x, from.y, from.z);
            source.move(player);
            player.connection.resetPosition();
            player.setOnGround(WALKING);
            player.applied.clear();
            source.addRegionTicket(TicketType.FORCED, east, 0, east);
            ((ChunkCacheProbe) source).auramSmoke$runDistanceManagerUpdates();
            check(source.hasChunk(east.x, east.z) && source.getChunkNow(east.x, east.z) == null,
                    "pending FULL before movement");
            startedTick = server.getTickCount();
            observerAtSend = observer.tickCount;
            long started = System.nanoTime();
            player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(from.x + 1, from.y, from.z, WALKING));
            duration = System.nanoTime() - started;
            for (int offset = 2; offset <= PACKET_COUNT; offset++) {
                player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(from.x + offset, from.y, from.z, WALKING));
            }
            if (CANDIDATE) {
                check(player.position().equals(from) && player.applied.isEmpty(), "pending movement defers before Entity.move");
            }
        } else {
            // The fixture uses an in-process connection, so the network manager does not tick it.
            player.connection.tick();
        }
        if (player.applied.size() == PACKET_COUNT) {
            var arrivals = IntStream.rangeClosed(1, PACKET_COUNT).mapToObj(offset -> from.x + offset).toList();
            check(player.applied.equals(arrivals), "packets applied in original order");
            check(player.position().equals(new Vec3(from.x + PACKET_COUNT, from.y, from.z)),
                    "server reaches the requested final position");
            check(source.getChunkNow(east.x, east.z) != null, "arrival preserved the FULL destination invariant");
            int observerTicks = observer.tickCount - observerAtSend;
            check(CANDIDATE ? observerTicks > 0 : observerTicks == 0, "observer tick count during the pending movement");
            trials.add(Map.of("first_packet_nanos", duration, "observer_ticks", observerTicks,
                    "walking", WALKING, "arrivals", arrivals));
            source.removeRegionTicket(TicketType.FORCED, east, 0, east);
            east = null;
            if (trials.size() == 5) {
                Files.writeString(Path.of("movement-packet-replay-results.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(trials));
                Files.writeString(Path.of("auram-" + MODE + "-pass.txt"), "PASS ordered movement and ready destination\n");
                server.halt(false);
            }
        } else {
            check(server.getTickCount() - startedTick < 60, "the position queue completes before its timeout");
        }
    }

    private ProbePlayer join(String name, long id) {
        var probe = new ProbePlayer(server, level, new GameProfile(new UUID(0, id), name));
        probe.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), probe) {
            @Override public void send(Packet<?> packet) { }
        };
        probe.getAbilities().mayfly = !WALKING;
        probe.getAbilities().flying = !WALKING;
        probe.noPhysics = !WALKING;
        probe.setPos(8.5, 90, 8.5 + (id - 1102));
        var players = (PlayerListAccess) server.getPlayerList();
        players.auramSmoke$players().add(probe);
        players.auramSmoke$playersByUUID().put(probe.getUUID(), probe);
        level.addNewPlayer(probe);
        return probe;
    }

    private static void check(boolean result, String description) {
        if (!result) throw new AssertionError(description);
    }

    private static final class ProbePlayer extends ServerPlayer {
        private final List<Double> applied = new ArrayList<>();

        private ProbePlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
            super(server, level, profile);
        }

        @Override
        public void move(MoverType type, Vec3 delta) {
            super.move(type, delta);
            if (type == MoverType.PLAYER) applied.add(getX());
        }
    }
}

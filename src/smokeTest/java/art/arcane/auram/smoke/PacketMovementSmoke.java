package art.arcane.auram.smoke;

import art.arcane.auram.smoke.mixin.PlayerListAccess;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Isolate the Forge destination load from the Dimensional Doors packet tail. */
public final class PacketMovementSmoke {
    private static final StackWalker STACK = StackWalker.getInstance();
    private static ChunkPos target;
    private static List<String> loadStack;

    public static void observeChunkLookup(int x, int z, ChunkStatus status, boolean create) {
        if (target == null || x != target.x || z != target.z || status != ChunkStatus.FULL || !create
                || !loadStack.isEmpty()) return;
        loadStack = STACK.walk(frames -> frames.limit(16).map(Object::toString).toList());
    }

    public static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var source = level.getChunkSource();
        var player = new ServerPlayer(server, level, new GameProfile(new UUID(0, 1101), "AuramMoveProbe"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) { }
        };
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        // A collision-free flight exercises the exact Forge load in Entity.move.
        player.noPhysics = true;
        var playerList = (PlayerListAccess) server.getPlayerList();
        playerList.auramSmoke$players().add(player);
        playerList.auramSmoke$playersByUUID().put(player.getUUID(), player);
        var results = new ArrayList<Map<String, Object>>();
        try {
            for (int trial = 0; trial < 5; trial++) {
                var home = new ChunkPos(24 + trial * 32, 24);
                var east = new ChunkPos(home.x + 1, home.z);
                level.getChunk(home.x, home.z);
                double x = home.getMinBlockX() + 15.5;
                double y = 82;
                double z = home.getMinBlockZ() + 8.5;
                player.setPos(x, y, z);
                if (trial == 0) level.addNewPlayer(player);
                source.move(player);
                player.connection.resetPosition();
                source.addRegionTicket(TicketType.FORCED, east, 0, east);
                ((ChunkCacheProbe) source).auramSmoke$runDistanceManagerUpdates();
                check(source.hasChunk(east.x, east.z) && source.getChunkNow(east.x, east.z) == null,
                        "the destination has a FULL dependency that is still pending");

                target = east;
                loadStack = List.of();
                long started = System.nanoTime();
                player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(x + 1, y, z, false));
                long elapsed = System.nanoTime() - started;
                target = null;
                check(player.position().equals(new Vec3(x + 1, y, z)), "stock movement finishes at the packet position");
                check(source.getChunkNow(east.x, east.z) != null, "stock packet completes with a FULL destination");
                check(loadStack.stream().anyMatch(frame -> frame.contains("Entity.m_20343_") || frame.contains("Entity.setPosRaw"))
                                && loadStack.stream().anyMatch(frame -> frame.contains("Entity.m_6478_") || frame.contains("Entity.move"))
                                && loadStack.stream().anyMatch(frame -> frame.contains("ServerGamePacketListenerImpl")),
                        "the main-thread FULL lookup came from the Forge entity move: " + loadStack);
                source.removeRegionTicket(TicketType.FORCED, east, 0, east);
                results.add(Map.of("destination", east.toString(), "packet_nanos", elapsed, "full_lookup_stack", loadStack));
            }
            Files.writeString(Path.of("movement-packet-results.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(results) + "\n");
        } finally {
            target = null;
            player.discard();
            playerList.auramSmoke$playersByUUID().remove(player.getUUID());
            playerList.auramSmoke$players().remove(player);
        }
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}

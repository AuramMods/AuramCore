package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DimDoorsCollisionSmoke {
    private static final int Y = 80;
    private static final ServerboundMovePlayerPacket PACKET = new ServerboundMovePlayerPacket.StatusOnly(false);
    private static final List<Hit> HITS = new ArrayList<>();
    private static ServerPlayer observedPlayer;
    private static BlockPos consumeAt;

    public static InteractionResult observe(ServerPlayer player, BlockPos position, Vec3 movement) {
        if (player != observedPlayer) return null;
        HITS.add(new Hit(position.immutable(), movement));
        return position.equals(consumeAt) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    static void run(MinecraftServer server, boolean reference) throws Exception {
        ServerLevel level = server.overworld();
        var dimensionalDoor = Class.forName("org.dimdev.dimdoors.block.door.DimensionalDoorBlock");
        Block door = ForgeRegistries.BLOCKS.getValues().stream().filter(dimensionalDoor::isInstance)
                .findFirst().orElseThrow(() -> new AssertionError("expected an installed DimDoors collision door"));
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "AuramDoorProbe"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) { }
        };
        // Exercise the real movement TAIL without letting Forge's independent
        // movement and physics paths request the same pending chunk first.
        player.wonGame = true;
        observedPlayer = player;
        try {
            ChunkPos left = new ChunkPos(-17, -17);
            ChunkPos right = new ChunkPos(left.x + 1, left.z);
            level.getChunk(left.x, left.z);
            level.getChunk(right.x, right.z);
            BlockPos west = placeDoor(level, door, left, 15);
            BlockPos east = placeDoor(level, door, right, 0);
            moveToBorder(player, left);
            check(level.hasChunksAt(west, east.above()), "ready fixture has all bounding chunks");
            consumeAt = east;
            player.connection.handleMovePlayer(PACKET);
            List<BlockPos> loadedOrder = List.of(west, west.above(), east);
            check(positions().equals(loadedOrder), "ready chunks preserve XYZ order and first consuming callback: " + HITS);
            List<String> loaded = HITS.stream().map(hit -> hit.pos().toShortString()).toList();
            double flightPacketMs = checkFlightPacket(level, player, door, reference);

            List<Double> pendingTimes = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                ChunkPos home = new ChunkPos(-200 - 8 * i, -200);
                ChunkPos pending = new ChunkPos(home.x + 1, home.z);
                level.getChunk(home.x, home.z);
                BlockPos firstDoor = placeDoor(level, door, home, 15);
                startPending(level, pending);
                moveToBorder(player, home);
                player.connection.resetPosition();
                player.setPos(player.getX() + 0.025, player.getY(), player.getZ());
                Vec3 originalMovement = new Vec3(0.025, 0, 0);
                consumeAt = null;
                HITS.clear();
                if (!reference) PatchSmokeMod.forbiddenChunkLookups.add(pending.toLong());
                long start = System.nanoTime();
                try {
                    player.connection.handleMovePlayer(PACKET);
                } finally {
                    PatchSmokeMod.forbiddenChunkLookups.clear();
                }
                pendingTimes.add((System.nanoTime() - start) / 1_000_000.0);
                if (reference) {
                    check(level.getChunkSource().getChunkNow(pending.x, pending.z) != null,
                            "stock handler synchronously resolves the pending chunk");
                    check(positions().equals(List.of(firstDoor, firstDoor.above())),
                            "stock pending path invokes callback in order");
                    continue;
                }
                check(HITS.isEmpty(), "pending collision waits before invoking any callback");
                check(level.getChunkSource().getChunkNow(pending.x, pending.z) == null,
                        "collision did not complete the future synchronously");

                level.getChunk(pending.x, pending.z);
                BlockPos secondDoor = placeDoor(level, door, pending, 0);
                consumeAt = secondDoor;
                if (i == 3) player.setPos(player.getX() - 2, player.getY(), player.getZ());
                if (i == 4) player.connection.teleport(player.getX(), player.getY(), player.getZ(), 0, 0);
                player.connection.tick();
                if (i < 3) {
                    check(positions().equals(List.of(firstDoor, firstDoor.above(), secondDoor)),
                            "ready future replays the entire collision in order");
                    for (Hit hit : HITS) {
                        check(hit.delta().distanceToSqr(originalMovement) < 1.0e-18,
                                "replay uses the original movement delta after resetPosition");
                    }
                    player.connection.tick();
                    check(HITS.size() == 3, "deferred collision fires once");
                } else {
                    check(HITS.isEmpty(), "moved or teleported player discards the pending collision");
                }
            }
            if (!reference) {
                ChunkPos home = new ChunkPos(-260, -260);
                ChunkPos missing = new ChunkPos(home.x + 1, home.z);
                level.getChunk(home.x, home.z);
                moveToBorder(player, home);
                HITS.clear();
                check(!level.getChunkSource().hasChunk(missing.x, missing.z), "no ticket for the neighbor");
                player.connection.handleMovePlayer(PACKET);
                check(HITS.isEmpty() && !level.getChunkSource().hasChunk(missing.x, missing.z),
                        "an absent neighbor neither runs callbacks nor acquires a ticket");
            }
            checkRealDoor(level, player, door);
            var result = Map.of("ready_callback_order", loaded, "pending_callback_ms", pendingTimes,
                    "pending_cases", 5, "flight_packet_ms", flightPacketMs, "real_rift_teleport", true,
                    "reference", reference);
            Files.writeString(Path.of("dimdoors-collision-results.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
        } finally {
            observedPlayer = null;
            consumeAt = null;
            HITS.clear();
        }
    }

    private static void checkRealDoor(ServerLevel level, ServerPlayer player, Block door) throws Exception {
        ChunkPos chunk = new ChunkPos(-18, -17);
        level.getChunk(chunk.x, chunk.z);
        BlockPos entrance = placeDoor(level, door, chunk, 5);
        BlockPos exit = placeDoor(level, door, chunk, 10);
        var rift = level.getBlockEntity(entrance);
        check(rift != null, "entrance rift block entity is present");
        Class<?> virtualTarget = Class.forName("org.dimdev.dimdoors.rift.targets.VirtualTarget");
        Object destination = Class.forName("org.dimdev.dimdoors.rift.targets.LocalReference")
                .getConstructor(BlockPos.class).newInstance(exit);
        rift.getClass().getMethod("setDestination", virtualTarget).invoke(rift, destination);
        for (BlockPos pos : List.of(entrance, entrance.above())) {
            level.setBlock(pos, level.getBlockState(pos).setValue(DoorBlock.FACING, Direction.WEST)
                    .setValue(DoorBlock.OPEN, true), Block.UPDATE_KNOWN_SHAPE);
        }

        observedPlayer = null;
        player.setPos(entrance.getX() + 0.6, Y + 0.1, entrance.getZ() + 0.5);
        player.connection.resetPosition();
        player.setPos(entrance.getX() + 0.9, Y + 0.1, entrance.getZ() + 0.5);
        player.connection.handleMovePlayer(PACKET);
        check(player.position().distanceToSqr(Vec3.atBottomCenterOf(exit)) < 4,
                "an open dimensional door teleported the player to the linked rift: " + player.position());
    }

    private static double checkFlightPacket(ServerLevel level, ServerPlayer player, Block door, boolean reference) {
        ChunkPos home = new ChunkPos(-190, -190);
        ChunkPos east = new ChunkPos(home.x + 1, home.z);
        level.getChunk(home.x, home.z);
        BlockPos firstDoor = placeDoor(level, door, home, 15);
        startPending(level, east);

        double x = home.getMinBlockX() + 15.35;
        double z = home.getMinBlockZ() + 8.5;
        player.wonGame = false;
        player.noPhysics = true;
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.setPos(x, Y, z);
        player.setLastSectionPos(SectionPos.of(player));
        player.connection.resetPosition();
        HITS.clear();
        consumeAt = null;
        var packet = new ServerboundMovePlayerPacket.Pos(x + 0.45, Y, z, false);
        long start = System.nanoTime();
        try {
            player.connection.handleMovePlayer(packet);
        } finally {
            player.wonGame = true;
        }
        double elapsed = (System.nanoTime() - start) / 1_000_000.0;
        check(Math.abs(player.getX() - (x + 0.45)) < 1.0e-8, "the actual flight packet advances the player");
        if (reference) {
            check(level.getChunkSource().getChunkNow(east.x, east.z) != null,
                    "stock flight packet completes the pending neighbor");
        } else {
            check(HITS.isEmpty() && level.getChunkSource().getChunkNow(east.x, east.z) == null,
                    "flight packet completes independently of the neighbor");
            level.getChunk(east.x, east.z);
            player.connection.tick();
        }
        check(positions().equals(List.of(firstDoor, firstDoor.above())), "flight callback keeps order");
        return elapsed;
    }

    private static void startPending(ServerLevel level, ChunkPos pending) {
        var source = level.getChunkSource();
        source.addRegionTicket(TicketType.FORCED, pending, 2, pending);
        ((ChunkCacheProbe) source).auramSmoke$runDistanceManagerUpdates();
        check(source.hasChunk(pending.x, pending.z), "FULL ticket exists");
        check(source.getChunkNow(pending.x, pending.z) == null, "FULL future is still pending");
    }

    private static void moveToBorder(ServerPlayer player, ChunkPos home) {
        player.setPos(home.getMinBlockX() + 15.8, Y, home.getMinBlockZ() + 8.5);
    }

    private static BlockPos placeDoor(ServerLevel level, Block door, ChunkPos chunk, int x) {
        BlockPos lower = new BlockPos(chunk.getMinBlockX() + x, Y, chunk.getMinBlockZ() + 8);
        level.setBlock(lower.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(lower, door.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(lower.above(), door.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_KNOWN_SHAPE);
        check(level.getBlockState(lower).getBlock() == door && level.getBlockState(lower.above()).getBlock() == door,
                "both test door halves are present");
        return lower;
    }

    private static List<BlockPos> positions() {
        return HITS.stream().map(Hit::pos).toList();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Hit(BlockPos pos, Vec3 delta) { }
}

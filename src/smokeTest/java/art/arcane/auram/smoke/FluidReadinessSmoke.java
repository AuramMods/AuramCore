package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class FluidReadinessSmoke {
    public static void run(MinecraftServer server, boolean reference) throws Exception {
        List<Fluid> fluids = new ArrayList<>(List.of(Fluids.EMPTY, Fluids.WATER, Fluids.LAVA));
        ForgeRegistries.FLUIDS.getValues().stream()
                .filter(f -> ForgeRegistries.FLUIDS.getKey(f).getNamespace().equals("mekanism"))
                .filter(f -> f.isSource(f.defaultFluidState()) && !f.defaultFluidState().createLegacyBlock().isAir())
                .sorted(Comparator.comparing(f -> ForgeRegistries.FLUIDS.getKey(f).toString())).forEach(fluids::add);
        check(fluids.size() > 3, "installed modded fluid is exercised");
        List<Map<String, Object>> snapshots = new ArrayList<>();
        List<String> fluidIds = fluids.stream().map(f -> ForgeRegistries.FLUIDS.getKey(f).toString()).toList();
        for (ServerLevel level : List.of(server.overworld(), server.getLevel(Level.NETHER), server.getLevel(Level.END))) {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level.getChunk(x, z);
            Entity[] samples = new Entity[4];
            for (int kind = 0; kind < 4; kind++) {
                samples[kind] = entity(server, level, kind, 8.5, 80.2, 8.5);
                samples[kind].stopRiding();
            }
            Boat vehicle = new Boat(level, 8.5, 80.2, 8.5);
            for (double[] site : new double[][]{{8.5, 8.5}, {15.95, 8.5}, {-0.05, -0.05}, {-15.95, 15.95}}) {
                for (Fluid fluid : fluids) for (boolean flowing : new boolean[]{false, true}) {
                    BlockState liquid = fluid.defaultFluidState().createLegacyBlock();
                    if (flowing && liquid.hasProperty(LiquidBlock.LEVEL)) liquid = liquid.setValue(LiquidBlock.LEVEL, 3);
                    int cx = (int) Math.floor(site[0]), cz = (int) Math.floor(site[1]);
                    for (int x = cx - 3; x <= cx + 3; x++) for (int z = cz - 3; z <= cz + 3; z++) {
                        for (int y = 79; y <= 83; y++) level.setBlock(new BlockPos(x, y, z), y == 79 ? Blocks.STONE.defaultBlockState()
                                : y == 80 ? liquid : Blocks.AIR.defaultBlockState(), 16);
                    }
                    for (int kind = 0; kind < 4; kind++) for (double y : new double[]{80.2, 80.95, 82.2}) {
                        for (boolean predicate : new boolean[]{false, true}) {
                            Entity entity = samples[kind];
                            ((FluidProbe) entity).auramSmoke$resetFluidState();
                            entity.moveTo(site[0], y, site[1], 0, 0);
                            if (kind == 3) {
                                vehicle.moveTo(site[0], y, site[1], 0, 0);
                                entity.startRiding(vehicle, true);
                            }
                            entity.setDeltaMovement(0.02, -0.03, 0.01);
                            entity.fallDistance = 12;
                            entity.setRemainingFireTicks(100);
                            boolean inFluid;
                            if (predicate) {
                                entity.updateFluidHeightAndDoFluidPushing(state -> state.getFluidType() != Fluids.WATER.getFluidType());
                                inFluid = entity.isInFluidType();
                            } else inFluid = ((FluidProbe) entity).auramSmoke$updateFluidState();
                            Map<String, Object> state = new LinkedHashMap<>();
                            state.put("dimension", level.dimension().location().toString());
                            state.put("site", List.of(site[0], site[1], y));
                            state.put("fluid", ForgeRegistries.FLUIDS.getKey(fluid).toString());
                            state.put("flowing", flowing); state.put("kind", kind); state.put("predicate", predicate);
                            state.put("in_fluid", inFluid); state.put("in_water", entity.isInWater());
                            state.put("in_lava", entity.isInLava()); state.put("fall_distance", entity.fallDistance);
                            state.put("fire_ticks", entity.getRemainingFireTicks());
                            state.put("velocity", List.of(entity.getDeltaMovement().x, entity.getDeltaMovement().y, entity.getDeltaMovement().z));
                            state.put("heights", fluids.stream().map(f -> entity.getFluidTypeHeight(f.getFluidType())).toList());
                            snapshots.add(state);
                            entity.stopRiding();
                        }
                    }
                }
            }
        }
        Files.writeString(Path.of("fluid-loaded-snapshots.json"), new GsonBuilder().setPrettyPrinting().create().toJson(snapshots) + "\n");
        if (!reference) pendingChecks(server);
        Files.writeString(Path.of("fluid-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "loaded_states", snapshots.size(), "fluids", fluidIds, "dimensions", 3,
                "pending_full_ticket_checked", !reference, "completed_chunk_retry_checked", !reference)) + "\n");
    }

    private static void pendingChecks(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ChunkPos home = new ChunkPos(-1000, 1000), east = new ChunkPos(home.x + 1, home.z);
        level.getChunk(home.x, home.z);
        level.getChunkSource().addRegionTicket(TicketType.FORCED, east, 2, east);
        ((ChunkCacheProbe) level.getChunkSource()).auramSmoke$runDistanceManagerUpdates();
        check(level.getChunkSource().hasChunk(east.x, east.z), "pending FULL ticket exists");
        check(level.getChunkSource().getChunkNow(east.x, east.z) == null, "FULL future is incomplete");
        PatchSmokeMod.forbiddenChunkLookups.add(east.toLong());
        try {
            for (int kind = 0; kind < 4; kind++) {
                Entity entity = entity(server, level, kind, home.getMinBlockX() + 15.8, 80.1, home.getMinBlockZ() + 8.5);
                check(!entity.touchingUnloadedChunk(), "original ticket guard accepts pending neighbor");
                Vec3 motion = new Vec3(0.1, -0.2, 0.3);
                entity.setDeltaMovement(motion);
                ((FluidProbe) entity).auramSmoke$updateFluidState();
                check(entity.getDeltaMovement().equals(motion), "pending scan leaves motion unchanged");
                check(!entity.isInFluidType(), "pending scan does not invent fluid heights");
                entity.stopRiding();
            }
        } finally { PatchSmokeMod.forbiddenChunkLookups.clear(); }
        level.getChunk(east.x, east.z);
        for (int x = home.getMinBlockX() + 13; x <= home.getMinBlockX() + 19; x++) {
            level.setBlock(new BlockPos(x, 80, home.getMinBlockZ() + 8), Blocks.WATER.defaultBlockState(), 16);
        }
        Entity player = entity(server, level, 1, home.getMinBlockX() + 15.8, 80.1, home.getMinBlockZ() + 8.5);
        player.fallDistance = 12;
        check(((FluidProbe) player).auramSmoke$updateFluidState(), "completed chunk restores ordinary fluid detection");
        check(player.isInWater() && player.fallDistance == 0, "water fall-distance behavior resumes");
    }

    private static Entity entity(MinecraftServer server, ServerLevel level, int kind, double x, double y, double z) {
        Entity entity = switch (kind) {
            case 0 -> new ArmorStand(level, x, y, z);
            case 1, 3 -> new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "FluidProbe"));
            case 2 -> new Boat(level, x, y, z);
            default -> throw new AssertionError();
        };
        if (entity instanceof ServerPlayer player) {
            player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player) {
                @Override public void send(Packet<?> packet) { }
            };
        }
        entity.moveTo(x, y, z, 0, 0);
        if (kind == 3) entity.startRiding(new Boat(level, x, y, z), true);
        return entity;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

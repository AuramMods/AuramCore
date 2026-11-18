package art.arcane.auram.smoke;

import art.arcane.auram.util.WellProximity;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.sammy.malum.common.capability.MalumLivingEntityDataCapability;
import com.sammy.malum.core.handlers.TouchOfDarknessHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/** Production-mapped mixins against the installed Malum and Lodestone jars. */
public final class WellProximitySmoke {
    public static void run(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        for (int x = -2; x <= 3; x++) for (int z = -2; z <= 3; z++) level.getChunk(x, z);
        Block conduit = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse("malum:void_conduit"));
        check(conduit != null && conduit != Blocks.AIR, "installed conduit block");
        for (BlockPos p : new BlockPos[]{new BlockPos(-7, 80, -7), new BlockPos(0, 80, 0),
                new BlockPos(15, 80, 16), new BlockPos(16, 84, 15), new BlockPos(23, 80, 23)}) {
            level.setBlock(p, conduit.defaultBlockState(), 16);
        }
        level.setBlock(new BlockPos(8, 80, 8), Blocks.CHEST.defaultBlockState(), 16);
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "WellProbe"));
        int cases = 0, positives = 0, negatives = 0;
        for (int x = -8; x < 24; x++) for (int z = -8; z < 24; z++) for (int y : new int[]{72, 80, 88}) {
            player.moveTo(x + 0.5, y, z + 0.5, 0, 0);
            boolean reference = TouchOfDarknessHandler.checkForWeepingWell(player).isPresent();
            Boolean candidate = WellProximity.find(level, player.blockPosition());
            check(candidate != null && candidate == reference, "loaded lookup equivalence " + player.blockPosition());
            if (reference) positives++; else negatives++;
            cases++;
        }
        check(positives > 0 && negatives > 0, "both presence and absence are exercised");

        ChunkPos home = new ChunkPos(1000, -1000);
        level.getChunk(home.x, home.z);
        BlockPos well = home.getMiddleBlockPosition(80);
        level.setBlock(well, conduit.defaultBlockState(), 16);
        player.moveTo(well.getX() + 0.5, 82, well.getZ() + 0.5, 0, 0);
        // Level.getBlockEntity uses IMMEDIATE rather than the chunk's default
        // CHECK policy. Reset each state before evaluating the two lookups.
        level.getBlockEntity(well).setRemoved();
        check(TouchOfDarknessHandler.checkForWeepingWell(player).isPresent(), "reference recreates removed conduit");
        level.getBlockEntity(well).setRemoved();
        PatchSmokeMod.forbiddenChunkLookups.add(home.toLong());
        try { check(Boolean.TRUE.equals(WellProximity.find(level, player.blockPosition())), "candidate recreates removed conduit without loading lookup"); }
        finally { PatchSmokeMod.forbiddenChunkLookups.clear(); }
        pendingTag(level, well);
        check(TouchOfDarknessHandler.checkForWeepingWell(player).isPresent(), "reference promotes pending conduit tag");
        pendingTag(level, well);
        PatchSmokeMod.forbiddenChunkLookups.add(home.toLong());
        try { check(Boolean.TRUE.equals(WellProximity.find(level, player.blockPosition())), "candidate promotes pending tag without loading lookup"); }
        finally { PatchSmokeMod.forbiddenChunkLookups.clear(); }
        TouchOfDarknessHandler handler = MalumLivingEntityDataCapability.getCapability(player).touchOfDarknessHandler;
        long time = level.getGameTime();
        server.getWorldData().overworldData().setGameTime(20);
        // Lodestone's extra positive boundary chunks cannot contain a candidate.
        for (int x = home.x; x <= home.x + 1; x++) for (int z = home.z; z <= home.z + 1; z++) {
            if (x != home.x || z != home.z) PatchSmokeMod.forbiddenChunkLookups.add(ChunkPos.asLong(x, z));
        }
        try {
            handler.isNearWeepingWell = false;
            TouchOfDarknessHandler.entityTick(new LivingEvent.LivingTickEvent(player));
            check(handler.isNearWeepingWell, "periodic update finds loaded well without boundary loads");
            level.setBlock(well, Blocks.AIR.defaultBlockState(), 16);
            TouchOfDarknessHandler.entityTick(new LivingEvent.LivingTickEvent(player));
            check(!handler.isNearWeepingWell, "loaded absence updates the state");
            player.moveTo(home.getMinBlockX() + 15.5, 82, well.getZ() + 0.5, 0, 0);
            for (boolean previous : new boolean[]{false, true}) {
                handler.isNearWeepingWell = previous;
                TouchOfDarknessHandler.entityTick(new LivingEvent.LivingTickEvent(player));
                check(handler.isNearWeepingWell == previous, "absent chunk defers both previous states");
            }
            ChunkPos east = new ChunkPos(home.x + 1, home.z);
            level.getChunkSource().addRegionTicket(TicketType.FORCED, east, 2, east);
            ((ChunkCacheProbe) level.getChunkSource()).auramSmoke$runDistanceManagerUpdates();
            check(level.getChunkSource().hasChunk(east.x, east.z), "FULL ticket present");
            check(level.getChunkSource().getChunkNow(east.x, east.z) == null, "ticket's FULL future is pending");
            for (boolean previous : new boolean[]{false, true}) {
                handler.isNearWeepingWell = previous;
                TouchOfDarknessHandler.entityTick(new LivingEvent.LivingTickEvent(player));
                check(handler.isNearWeepingWell == previous, "pending chunk defers both previous states");
            }
        } finally {
            PatchSmokeMod.forbiddenChunkLookups.clear();
            server.getWorldData().overworldData().setGameTime(time);
        }
        // Once the future completes, the next ordinary periodic scan sees it.
        ChunkPos east = new ChunkPos(home.x + 1, home.z);
        level.getChunk(east.x, east.z);
        level.setBlock(new BlockPos(east.getMinBlockX(), 80, well.getZ()), conduit.defaultBlockState(), 16);
        server.getWorldData().overworldData().setGameTime(40);
        handler.isNearWeepingWell = false;
        TouchOfDarknessHandler.entityTick(new LivingEvent.LivingTickEvent(player));
        check(handler.isNearWeepingWell, "completed neighbor is detected at the next scan");
        check(TouchOfDarknessHandler.checkForWeepingWell(player).isPresent(), "shared rejection query retains original results");
        server.getWorldData().overworldData().setGameTime(time);
        Files.writeString(Path.of("well-proximity-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "loaded_comparisons", cases, "positive_comparisons", positives, "negative_comparisons", negatives,
                "missing_and_pending_states", 4, "periodic_mixin_checked", true,
                "boundary_chunks_remain_unloaded", true, "completed_chunk_retry_checked", true,
                "removed_block_entity_checked", true, "pending_block_entity_tag_checked", true)) + "\n");
    }

    private static void pendingTag(ServerLevel level, BlockPos pos) {
        CompoundTag tag = level.getBlockEntity(pos).saveWithFullMetadata();
        var chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        chunk.removeBlockEntity(pos);
        chunk.setBlockEntityNbt(tag);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

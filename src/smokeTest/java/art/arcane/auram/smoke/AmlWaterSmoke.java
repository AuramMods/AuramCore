package art.arcane.auram.smoke;

import art.arcane.auram.smoke.mixin.EntityBlockPositionProbe;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AmlWaterSmoke {
    private static final String AML = "com.github.minecraftschurlimods.arsmagicalegacy";
    private static final ResourceLocation ENDER = ResourceLocation.tryBuild("arsmagicalegacy", "ender");

    public static void run(MinecraftServer server, boolean reference) throws Exception {
        ServerLevel overworld = server.overworld();
        ServerLevel nether = server.getLevel(Level.NETHER);
        ServerPlayer player = new ServerPlayer(server, overworld, new GameProfile(new UUID(0, 7931), "AuramWaterProbe"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) { }
        };
        var probe = (EntityBlockPositionProbe) player;
        var abilities = Class.forName(AML + ".common.handler.TickHandler").getDeclaredMethod("handleAbilities", Player.class);
        abilities.setAccessible(true);
        UUID modifier = (UUID) Class.forName(AML + ".common.affinity.AbilityUUIDs").getField("HEALTH_REDUCTION").get(null);
        var api = Class.forName(AML + ".api.ArsMagicaAPI");
        Object affinity = api.getMethod("getAffinityHelper").invoke(api.getMethod("get").invoke(null));
        Method setDepth = Class.forName(AML + ".api.affinity.IAffinityHelper")
                .getMethod("setAffinityDepth", Player.class, ResourceLocation.class, float.class);

        List<Outcome> loadedResults = new ArrayList<>();
        BlockPos sky = new BlockPos(24, 100, 24);
        overworld.getChunkAt(sky);
        player.setPos(sky.getX() + 0.5, sky.getY(), sky.getZ() + 0.5);
        check(overworld.getBrightness(LightLayer.SKY, sky) == 15, "Overworld fixture has sky light");
        compareLoaded(player, probe, abilities, setDepth, affinity, modifier, sky, "sky", loadedResults);

        BlockPos entry = new BlockPos(-120, 100, -120);
        nether.getChunkAt(entry);
        player.teleportTo(nether, entry.getX() + 0.5, entry.getY(), entry.getZ() + 0.5, 0, 0);
        check(player.level() == nether, "player entered the Nether");
        check(nether.getBrightness(LightLayer.SKY, entry) == 0, "Nether fixture has no sky light");
        compareLoaded(player, probe, abilities, setDepth, affinity, modifier, entry, "nether", loadedResults);

        List<Pending> pendingResults = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            var attributes = player.getAttribute(Attributes.MAX_HEALTH);
            probe.auramSmoke$setCachedPosition(entry);
            player.setHealth(20);
            setDepth.invoke(affinity, player, ENDER, 0.75F);
            setBlock(nether, entry, true);
            probe.auramSmoke$setInWater(false);
            abilities.invoke(null, player);
            check(Math.abs(player.getMaxHealth() - 17) < 0.001 && player.getHealth() == 17,
                    "loaded bubble applies the reduction and clamps health");

            boolean eligible = (i & 1) == 0;
            if (!eligible) setDepth.invoke(affinity, player, ENDER, 0.1F);
            ChunkPos pending = new ChunkPos(-100 - i * 8, -100);
            var source = nether.getChunkSource();
            source.addRegionTicket(TicketType.FORCED, pending, 2, pending);
            ((ChunkCacheProbe) source).auramSmoke$runDistanceManagerUpdates();
            check(source.hasChunk(pending.x, pending.z) && source.getChunkNow(pending.x, pending.z) == null, "FULL future is pending");

            // Forge's separate movement lookup can wait before AML runs. Model
            // the arriving block position directly to isolate the AML read.
            BlockPos arriving = pending.getMiddleBlockPosition(100);
            probe.auramSmoke$setCachedPosition(arriving);
            if (!reference) PatchSmokeMod.forbiddenChunkLookups.add(pending.toLong());
            long started = System.nanoTime();
            try {
                abilities.invoke(null, player);
            } finally {
                PatchSmokeMod.forbiddenChunkLookups.clear();
            }
            long nanos = System.nanoTime() - started;
            boolean ready = source.getChunkNow(pending.x, pending.z) != null;
            check(ready == reference, "only the reference AML read completes a pending FULL future");
            check((attributes.getModifier(modifier) != null) == (!reference && eligible), "correct reduction while readiness is unresolved");
            pendingResults.add(new Pending(eligible, ready, player.getMaxHealth(), player.getHealth(), nanos));

            nether.getChunkAt(arriving);
            setBlock(nether, arriving, false);
            abilities.invoke(null, player);
            check(attributes.getModifier(modifier) == null && player.getMaxHealth() == 20,
                    "ready dry destination removes the reduction");
        }

        var source = nether.getChunkSource();
        ChunkPos stillPending = new ChunkPos(-166, -103);
        source.addRegionTicket(TicketType.FORCED, stillPending, 2, stillPending);
        ((ChunkCacheProbe) source).auramSmoke$runDistanceManagerUpdates();
        probe.auramSmoke$setCachedPosition(stillPending.getMiddleBlockPosition(100));
        probe.auramSmoke$setInWater(true);
        setDepth.invoke(affinity, player, ENDER, 0.75F);
        player.setHealth(20);
        guardedAbilityCheck(player, abilities, stillPending);
        check(source.getChunkNow(stillPending.x, stillPending.z) == null &&
                player.getMaxHealth() == 17 && player.getHealth() == 17, "cached water still clamps health during a pending FULL");

        if (!reference) {
            ChunkPos missing = new ChunkPos(-192, -118);
            probe.auramSmoke$setCachedPosition(missing.getMiddleBlockPosition(100));
            probe.auramSmoke$setInWater(false);
            setDepth.invoke(affinity, player, ENDER, 0.1F);
            guardedAbilityCheck(player, abilities, missing);
            check(!source.hasChunk(missing.x, missing.z) && player.getMaxHealth() == 20,
                    "ineligible player clears the reduction without starting a chunk request");
        }
        probe.auramSmoke$setCachedPosition(entry);
        Files.writeString(Path.of("aml-water-results.json"), new GsonBuilder().setPrettyPrinting().create()
                .toJson(Map.of("loaded", loadedResults, "pending", pendingResults, "reference", reference, "cached_water", true)) + "\n");
    }

    private static void guardedAbilityCheck(Player player, Method abilities, ChunkPos pos) throws Exception {
        PatchSmokeMod.forbiddenChunkLookups.add(pos.toLong());
        try {
            abilities.invoke(null, player);
        } finally {
            PatchSmokeMod.forbiddenChunkLookups.clear();
        }
    }

    private static void compareLoaded(ServerPlayer player, EntityBlockPositionProbe probe, Method abilities, Method setDepth, Object affinity,
                                      UUID id, BlockPos pos, String label, List<Outcome> outcome) throws Exception {
        var attributes = player.getAttribute(Attributes.MAX_HEALTH);
        for (float depth : new float[]{0, 0.499F, 0.5F, 0.75F, 0.98F, 0.99F, 1}) {
            for (int state = 0; state < 3; state++) {
                attributes.removeModifier(id);
                player.setHealth(20);
                setDepth.invoke(affinity, player, ENDER, depth);
                setBlock(player.serverLevel(), pos, state == 1);
                probe.auramSmoke$setInWater(state == 2);
                abilities.invoke(null, player);
                var modifier = attributes.getModifier(id);
                outcome.add(new Outcome(label, state, depth, player.getMaxHealth(), player.getHealth(),
                        modifier == null ? null : modifier.getAmount()));
            }
        }
    }

    private static void setBlock(ServerLevel level, BlockPos pos, boolean bubble) {
        level.setBlock(pos, (bubble ? Blocks.BUBBLE_COLUMN : Blocks.AIR).defaultBlockState(), Block.UPDATE_KNOWN_SHAPE);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Outcome(String dimension, int waterState, float depth, float maximum, float health, Double modifier) {
    }

    private record Pending(boolean eligible, boolean ready, float maximum, float health, long nanos) {
    }
}

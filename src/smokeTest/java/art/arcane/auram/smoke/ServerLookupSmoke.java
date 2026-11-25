package art.arcane.auram.smoke;

import art.arcane.auram.util.PortalChunkPreparation;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.fml.ModList;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class ServerLookupSmoke {
    static void run(MinecraftServer server) throws Exception {
        testPortalScope(server);
        if (ModList.get().isLoaded("ftbchunks")) testFtbCache(server);
        if (ModList.get().isLoaded("chunkactivitytracker")) testActivity(server);
    }

    private static void testPortalScope(MinecraftServer server) {
        var level = server.overworld();
        var origin = level.getSharedSpawnPos();
        var pos = new ChunkPos(origin);
        level.getChunk(pos.x, pos.z);
        var chunks = level.getChunkSource();
        var outer = new PortalChunkPreparation(chunks, origin);
        var inner = new PortalChunkPreparation(chunks, origin.above());
        check(PortalChunkPreparation.active() == null, "initial portal scope");
        check(outer.ready(() -> {
            check(PortalChunkPreparation.active() == outer, "outer portal scope");
            check(chunks.getChunk(pos.x, pos.z, ChunkStatus.FULL, true) == chunks.getChunkNow(pos.x, pos.z),
                    "prepared loaded chunk lookup");
            check(CompletableFuture.supplyAsync(PortalChunkPreparation::active).join() == null, "other thread isolation");
            check(inner.ready(() -> check(PortalChunkPreparation.active() == inner, "inner portal scope")), "inner readiness");
            PortalChunkPreparation.outsideLookup(() -> check(PortalChunkPreparation.active() == null, "POI read isolation"));
            check(PortalChunkPreparation.active() == outer, "restored outer scope");
            PortalChunkPreparation.finishBeforeCreation(chunks);
            throw new AssertionError("creation continuation executed during the probe");
        }), "portal creation handed back to transfer");
        check(!outer.ready(() -> outer.readChunk(pos.x + 55, pos.z + 55)), "cold portal read pauses");
        check(PortalChunkPreparation.active() == null, "cold portal scope cleaned");
        try {
            inner.ready(() -> { throw new IllegalArgumentException("fixture"); });
            throw new AssertionError("exception swallowed");
        } catch (IllegalArgumentException expected) {
            check(PortalChunkPreparation.active() == null, "exception portal scope cleaned");
        }
    }

    private static void testFtbCache(MinecraftServer server) throws Exception {
        var type = Class.forName("dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl");
        var manager = type.getMethod("getInstance").invoke(null);
        var query = type.getMethod("getForceLoadedChunks", net.minecraft.resources.ResourceKey.class);
        var clear = type.getMethod("clearForceLoadedCache");
        var cache = type.getDeclaredField("forceLoadedChunkCache");
        cache.setAccessible(true);
        var dimension = server.overworld().dimension();
        var claimed = new Long2ObjectOpenHashMap<UUID>();
        long key = ChunkPos.asLong(-15, 24);
        claimed.put(key, UUID.randomUUID());
        clear.invoke(manager);
        check(((Map<?, ?>) query.invoke(manager, dimension)).isEmpty(), "empty claims initialize");
        cache.set(manager, Map.of(dimension, claimed));
        check(query.invoke(manager, dimension) == claimed, "dimension claim lookup");
        check(((Map<?, ?>) query.invoke(manager, Level.NETHER)).isEmpty(), "dimension claim never crosses levels");
        clear.invoke(manager);
        check(((Map<?, ?>) query.invoke(manager, dimension)).isEmpty(), "clearing claims discards old dimension map");
    }

    @SuppressWarnings("unchecked")
    private static void testActivity(MinecraftServer server) throws Exception {
        var infoType = Class.forName("toni.chunkactivitytracker.data.ChunkActivityInfo");
        var mapType = Class.forName("toni.chunkactivitytracker.data.ChunkActivityMap");
        var tracker = Class.forName("toni.chunkactivitytracker.ChunkActivityTracker");
        var infoAt = mapType.getMethod("getOrCreateChunkMap", net.minecraft.resources.ResourceKey.class);
        var records = mapType.getMethod("getChunks");
        var getTotal = tracker.getMethod("getTotalTimeInChunk", Level.class, ChunkPos.class);
        var update = infoType.getMethod("updatePlayerTime", UUID.class);
        var raw = infoType.getMethod("getPlayerTimeMap");
        var level = server.overworld();
        var pos = new ChunkPos(109, -135);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var record = infoType.getConstructor(Map.class, Map.class, long[].class)
                .newInstance(Map.of(first, 2L, second, 8L), Map.of(), new long[0]);
        var map = (ConcurrentHashMap<Long, Object>) records.invoke(infoAt.invoke(null, level.dimension()));
        check((long) getTotal.invoke(null, level, pos) == 0L, "missing activity entry");
        map.put(pos.toLong(), record);
        check((long) getTotal.invoke(null, level, pos) == 10L, "loaded activity sum");
        update.invoke(record, first);
        update.invoke(record, second);
        check((long) getTotal.invoke(null, level, pos) == 12L, "writes visible in current tick");
        var exposed = (ConcurrentHashMap<UUID, Long>) raw.invoke(record);
        exposed.put(second, 120L);
        check((long) getTotal.invoke(null, level, pos) == 123L, "external map put");
        exposed.values().remove(120L);
        check((long) getTotal.invoke(null, level, pos) == 3L, "external map view removal");
        map.remove(pos.toLong());
        check((long) getTotal.invoke(null, level, pos) == 0L, "removed activity entry");
    }

    private static void check(boolean condition, String test) {
        if (!condition) throw new AssertionError(test);
    }
}

package art.arcane.auram.map;

import art.arcane.auram.Auram;
import art.arcane.auram.AuramConfig;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.IdentityHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Auram.MODID)
public final class SurfaceMaps {
    private static final int REFRESH_DELAY = 20;
    private static final int MAX_REFRESHES_PER_TICK = 4;
    // Only the server thread accesses dirty. World generation threads only submit snapshots.
    private static final Map<ServerLevel, Long2LongMap> dirty = new IdentityHashMap<>();
    private static volatile SurfaceTileStore store;

    private SurfaceMaps() {}

    @SubscribeEvent
    public static void onStarting(ServerAboutToStartEvent event) {
        if (AuramConfig.SERVER.surfaceMapEnabled.get()) {
            store = new SurfaceTileStore(event.getServer().getWorldPath(LevelResource.ROOT).resolve("auram-surface-map"));
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        SurfaceTileStore previous = store;
        store = null;
        dirty.clear();
        if (previous != null) {
            previous.close();
        }
    }

    public static void generated(ServerLevel level, ChunkAccess chunk) {
        SurfaceTileStore tiles = store;
        if (tiles != null) {
            tiles.capture(level, chunk, false);
        }
    }

    @SubscribeEvent
    public static void onChunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            // Forge fires inside promotion to FULL. Use the supplied chunk and no level lookups.
            generated(level, chunk);
        }
    }

    public static void blockChanged(ServerLevel level, BlockPos pos) {
        if (store == null || !level.getServer().isSameThread()) {
            return;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || (!level.dimensionType().hasCeiling()
                && pos.getY() < chunk.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX() & 15, pos.getZ() & 15) - 4)) {
            return;
        }
        dirty.computeIfAbsent(level, ignored -> new Long2LongOpenHashMap())
                .putIfAbsent(chunk.getPos().toLong(), level.getGameTime() + REFRESH_DELAY);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.LevelTickEvent event) {
        SurfaceTileStore tiles = store;
        if (tiles == null || event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        Long2LongMap pending = dirty.get(level);
        if (pending == null) {
            return;
        }
        int processed = 0;
        var entries = pending.long2LongEntrySet().iterator();
        while (entries.hasNext() && processed < MAX_REFRESHES_PER_TICK) {
            var entry = entries.next();
            if (entry.getLongValue() > level.getGameTime()) {
                continue;
            }
            long pos = entry.getLongKey();
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(pos), ChunkPos.getZ(pos));
            if (chunk != null && !tiles.capture(level, chunk, true)) {
                break;
            }
            entries.remove();
            processed++;
        }
    }

    @SubscribeEvent
    public static void onLevelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            dirty.remove(level);
        }
    }
}

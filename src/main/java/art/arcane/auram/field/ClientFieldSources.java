package art.arcane.auram.field;

import art.arcane.auram.Auram;
import art.arcane.auram.vapor.VaporNetwork;
import art.arcane.auram.wind.WindNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.common.util.LogicalSidedProvider;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Client-owned sources live as long as their owning level and anchor chunk. */
@Mod.EventBusSubscriber(modid = Auram.MODID)
public final class ClientFieldSources {
    public static final Sources<VaporNetwork.Emitter> VAPOR = new Sources<>(2048);
    public static final Sources<WindNetwork.Emitter> WIND = new Sources<>(4096);

    private ClientFieldSources() {}

    public record Key(ResourceLocation provider, BlockPos anchor) {
        public Key {
            Objects.requireNonNull(provider, "provider");
            anchor = anchor.immutable();
        }
    }

    public static void requireClientThread(Level level) {
        if (!level.isClientSide() || !LogicalSidedProvider.WORKQUEUE.get(LogicalSide.CLIENT).isSameThread()) {
            throw new IllegalStateException("Client field sources must be updated on the client level thread");
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide()) {
            VAPOR.levels.remove(level);
            WIND.levels.remove(level);
        }
    }

    @SubscribeEvent
    public static void unloadChunk(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide()) {
            long chunk = event.getChunk().getPos().toLong();
            VAPOR.unload(level, chunk);
            WIND.unload(level, chunk);
        }
    }

    public static final class Sources<T> {
        private final int capacity;
        private final Map<Level, Tracked<T>> levels = new IdentityHashMap<>();

        private Sources(int capacity) {
            this.capacity = capacity;
        }

        public boolean set(Level level, Key key, T value) {
            requireClientThread(level);
            if (level.isOutsideBuildHeight(key.anchor()) || !level.hasChunkAt(key.anchor())) return false;
            var tracked = levels.computeIfAbsent(level, unused -> new Tracked<>());
            var section = tracked.chunks.computeIfAbsent(chunk(key.anchor()), unused -> new LinkedHashMap<>());
            if (section.containsKey(key)) {
                section.put(key, value);
                return true;
            }
            if (tracked.size == capacity) {
                if (section.isEmpty()) tracked.chunks.remove(chunk(key.anchor()));
                return false;
            }
            section.put(key, value);
            tracked.size++;
            return true;
        }

        public boolean stop(Level level, Key key) {
            requireClientThread(level);
            var tracked = levels.get(level);
            if (tracked == null) return false;
            long chunk = chunk(key.anchor());
            var section = tracked.chunks.get(chunk);
            if (section == null || section.remove(key) == null) return false;
            if (section.isEmpty()) tracked.chunks.remove(chunk);
            if (--tracked.size == 0) levels.remove(level);
            return true;
        }

        public boolean has(Level level) {
            return levels.containsKey(level);
        }

        /** Visits only owned anchors in columns touching this voxel window. */
        public void collect(Level level, VoxelGrid.Position origin, List<T> into) {
            var tracked = levels.get(level);
            if (tracked == null) return;
            int minX = origin.x() - 1, minY = origin.y() - 1, minZ = origin.z() - 1;
            int maxX = origin.x() + VoxelGrid.WINDOW_BLOCKS;
            int maxY = origin.y() + VoxelGrid.WINDOW_BLOCKS;
            int maxZ = origin.z() + VoxelGrid.WINDOW_BLOCKS;
            for (int x = minX >> 4; x <= maxX >> 4; x++) {
                for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                    var entries = tracked.chunks.get(ChunkPos.asLong(x, z));
                    if (entries == null) continue;
                    for (var entry : entries.entrySet()) {
                        BlockPos anchor = entry.getKey().anchor();
                        if (anchor.getX() >= minX && anchor.getX() <= maxX && anchor.getY() >= minY
                                && anchor.getY() <= maxY && anchor.getZ() >= minZ && anchor.getZ() <= maxZ) {
                            into.add(entry.getValue());
                        }
                    }
                }
            }
        }

        private void unload(Level level, long chunk) {
            var tracked = levels.get(level);
            if (tracked == null) return;
            var removed = tracked.chunks.remove(chunk);
            if (removed != null) tracked.size -= removed.size();
            if (tracked.size == 0) levels.remove(level);
        }

        private static long chunk(BlockPos anchor) {
            return ChunkPos.asLong(anchor.getX() >> 4, anchor.getZ() >> 4);
        }
    }

    private static final class Tracked<T> {
        private final Map<Long, Map<Key, T>> chunks = new HashMap<>();
        private int size;
    }

    /** Old frames identify sources by provider and vent. Match each owned source with at most one old copy. */
    public static <T, R> List<R> mergeLegacy(List<T> remote, List<T> local, Function<T, Key> wireKey,
                                              Function<T, R> convert) {
        if (local.isEmpty()) return remote.stream().map(convert).toList();
        var matches = new HashMap<Key, Integer>();
        for (T source : local) matches.merge(wireKey.apply(source), 1, Integer::sum);
        var merged = new ArrayList<R>(remote.size() + local.size());
        for (T source : remote) {
            var key = wireKey.apply(source);
            Integer count = matches.get(key);
            if (count == null) merged.add(convert.apply(source));
            else if (count == 1) matches.remove(key);
            else matches.put(key, count - 1);
        }
        for (T source : local) merged.add(convert.apply(source));
        return merged;
    }
}

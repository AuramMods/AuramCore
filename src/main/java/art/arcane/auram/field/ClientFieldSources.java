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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Client-owned sources live as long as their owning level and anchor chunk. */
@Mod.EventBusSubscriber(modid = Auram.MODID)
public final class ClientFieldSources {
    public static final Sources<VaporNetwork.Emitter> VAPOR = new Sources<>(2048, VaporNetwork.Emitter::position);
    public static final Sources<WindNetwork.Emitter> WIND = new Sources<>(4096, emitter -> emitter.flow().position());

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
        private final Function<T, BlockPos> vent;
        private final Map<Level, Tracked<T>> levels = new IdentityHashMap<>();

        private Sources(int capacity, Function<T, BlockPos> vent) {
            this.capacity = capacity;
            this.vent = vent;
        }

        public boolean set(Level level, Key key, T value) {
            requireClientThread(level);
            if (level.isOutsideBuildHeight(key.anchor()) || !level.hasChunkAt(key.anchor())) return false;
            var tracked = levels.computeIfAbsent(level, unused -> new Tracked<>());
            T previous = tracked.sources.get(key);
            if (value.equals(previous)) return true;
            long ventChunk = chunk(vent.apply(value));
            if (previous == null) {
                if (tracked.sources.size() >= capacity) return false;
                add(tracked.anchors, chunk(key.anchor()), key);
                add(tracked.vents, ventChunk, key);
            } else {
                long previousChunk = chunk(vent.apply(previous));
                if (previousChunk != ventChunk) {
                    remove(tracked.vents, previousChunk, key);
                    add(tracked.vents, ventChunk, key);
                }
            }
            tracked.sources.put(key, value);
            return true;
        }

        public boolean stop(Level level, Key key) {
            requireClientThread(level);
            var tracked = levels.get(level);
            if (tracked == null) return false;
            T previous = tracked.sources.remove(key);
            if (previous == null) return false;
            remove(tracked.anchors, chunk(key.anchor()), key);
            remove(tracked.vents, chunk(vent.apply(previous)), key);
            if (tracked.sources.isEmpty()) levels.remove(level);
            return true;
        }

        public boolean has(Level level) {
            return levels.containsKey(level);
        }

        public boolean contains(Level level, Key key) {
            var tracked = levels.get(level);
            return tracked != null && tracked.sources.containsKey(key);
        }

        /** Visits only emitters with vents in columns touching the voxel window. */
        public void collect(Level level, VoxelGrid.Position origin, List<T> into) {
            var tracked = levels.get(level);
            if (tracked == null) return;
            var first = origin.section();
            var last = VoxelGrid.VAPOR.lastSection(origin);
            for (int x = first.x(); x <= last.x(); x++) {
                for (int z = first.z(); z <= last.z(); z++) {
                    var entries = tracked.vents.get(ChunkPos.asLong(x, z));
                    if (entries == null) continue;
                    for (Key key : entries) {
                        T source = tracked.sources.get(key);
                        BlockPos pos = vent.apply(source);
                        if (VoxelGrid.VAPOR.contains(pos.getX() - origin.x(), pos.getY() - origin.y(), pos.getZ() - origin.z())) {
                            into.add(source);
                        }
                    }
                }
            }
        }

        private void unload(Level level, long chunk) {
            var tracked = levels.get(level);
            if (tracked == null) return;
            var removed = tracked.anchors.remove(chunk);
            if (removed != null) {
                for (Key key : removed) {
                    T source = tracked.sources.remove(key);
                    remove(tracked.vents, chunk(vent.apply(source)), key);
                }
            }
            if (tracked.sources.isEmpty()) levels.remove(level);
        }

        private static void add(Map<Long, Set<Key>> index, long chunk, Key key) {
            index.computeIfAbsent(chunk, unused -> new LinkedHashSet<>()).add(key);
        }

        private static void remove(Map<Long, Set<Key>> index, long chunk, Key key) {
            var column = index.get(chunk);
            column.remove(key);
            if (column.isEmpty()) index.remove(chunk);
        }

        private static long chunk(BlockPos anchor) {
            return ChunkPos.asLong(anchor.getX() >> 4, anchor.getZ() >> 4);
        }
    }

    private static final class Tracked<T> {
        private final Map<Key, T> sources = new HashMap<>();
        private final Map<Long, Set<Key>> anchors = new HashMap<>();
        private final Map<Long, Set<Key>> vents = new HashMap<>();
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

package art.arcane.auram.light;

import art.arcane.auram.Auram;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Auram.MODID)
public final class LightSources {
    private static final int MAX_PER_LEVEL = 4096;
    private static final int RANGE = 48;
    private static final int REFRESH_TICKS = 40;
    private static final Map<Level, Map<Key, LightSample>> LOCAL = new IdentityHashMap<>();
    private static final Map<ServerLevel, Tracked> SERVER = new IdentityHashMap<>();

    record Key(ResourceLocation id, BlockPos position) {
        Key {
            Objects.requireNonNull(id, "id");
            position = position.immutable();
        }
    }

    private LightSources() {}

    static boolean set(Level level, Key key, LightSample light) {
        if (level instanceof ServerLevel server) {
            Tracked tracked = SERVER.computeIfAbsent(server, ignored -> new Tracked());
            if (full(tracked.sources, key)) return false;
            tracked.dirty |= !light.equals(tracked.sources.put(key, light));
            return true;
        }
        var local = LOCAL.computeIfAbsent(level, ignored -> new HashMap<>());
        if (full(local, key)) return false;
        local.put(key, light);
        return true;
    }

    private static boolean full(Map<Key, LightSample> sources, Key key) {
        return sources.size() >= MAX_PER_LEVEL && !sources.containsKey(key);
    }

    static boolean stop(Level level, Key key) {
        if (level instanceof ServerLevel server) {
            Tracked tracked = SERVER.get(server);
            if (tracked == null || tracked.sources.remove(key) == null) return false;
            tracked.dirty = true;
            return true;
        }
        var local = LOCAL.get(level);
        return local != null && local.remove(key) != null;
    }

    static Collection<LightSample> local(Level level) {
        var local = LOCAL.get(level);
        return local == null ? List.of() : local.values();
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel server) SERVER.remove(server);
        else if (event.getLevel() instanceof Level level) LOCAL.remove(level);
    }

    @SubscribeEvent
    public static void unloadChunk(ChunkEvent.Unload event) {
        var chunk = event.getChunk().getPos();
        if (event.getLevel() instanceof ServerLevel server) {
            var tracked = SERVER.get(server);
            if (tracked != null) tracked.dirty |= tracked.sources.keySet().removeIf(key ->
                    key.position.getX() >> 4 == chunk.x && key.position.getZ() >> 4 == chunk.z);
        } else if (event.getLevel() instanceof Level level) {
            var local = LOCAL.get(level);
            if (local != null) local.keySet().removeIf(key ->
                    key.position.getX() >> 4 == chunk.x && key.position.getZ() >> 4 == chunk.z);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        Tracked tracked = SERVER.get(level);
        if (tracked == null) return;
        long now = level.getGameTime();
        tracked.dirty |= tracked.sources.values().removeIf(light -> light.expired(now));
        if (tracked.dirty || (now & 3) == 0) tracked.dispatch(level, now);
        if (tracked.sources.isEmpty() && tracked.sent.isEmpty()) SERVER.remove(level);
    }

    static void expireLocal(Level level) {
        var local = LOCAL.get(level);
        if (local != null) {
            local.values().removeIf(light -> light.expired(level.getGameTime()));
            if (local.isEmpty()) LOCAL.remove(level);
        }
    }

    private static boolean nearby(BlockPos position, BlockPos viewer) {
        return Math.abs(position.getX() - viewer.getX()) <= RANGE
                && Math.abs(position.getY() - viewer.getY()) <= RANGE
                && Math.abs(position.getZ() - viewer.getZ()) <= RANGE;
    }

    private record Sent(List<LightSample> lights, long refreshAt) {}

    private static final class Tracked {
        private final Map<Key, LightSample> sources = new HashMap<>();
        private final Map<UUID, Sent> sent = new HashMap<>();
        private boolean dirty;

        private void dispatch(ServerLevel level, long now) {
            var present = new HashSet<UUID>();
            for (ServerPlayer player : level.players()) {
                if (!LightNetwork.available(player)) continue;
                UUID viewer = player.getUUID();
                present.add(viewer);
                BlockPos origin = player.blockPosition();
                List<LightSample> selected = sources.entrySet().stream()
                        .filter(entry -> nearby(entry.getKey().position, origin))
                        .sorted(Comparator.<Map.Entry<Key, LightSample>>comparingDouble(entry ->
                                        entry.getKey().position.distToCenterSqr(player.position()))
                                .thenComparing(entry -> entry.getKey().id)
                                .thenComparing(entry -> entry.getKey().position))
                        .limit(LightNetwork.MAX_PER_CLIENT)
                        .map(Map.Entry::getValue).toList();
                Sent previous = sent.get(viewer);
                if (previous == null ? selected.isEmpty()
                        : selected.equals(previous.lights()) && now < previous.refreshAt()) continue;
                LightNetwork.send(player, new LightNetwork.Frame(level.dimension().location(), selected));
                if (selected.isEmpty()) sent.remove(viewer);
                else sent.put(viewer, new Sent(selected, now + REFRESH_TICKS));
            }
            sent.keySet().retainAll(present);
            dirty = false;
        }
    }
}

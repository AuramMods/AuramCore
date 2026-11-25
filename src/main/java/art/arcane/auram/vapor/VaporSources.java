package art.arcane.auram.vapor;

import art.arcane.auram.Auram;
import art.arcane.auram.field.ClientFieldSources.Key;
import art.arcane.auram.field.FieldNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Auram.MODID)
public final class VaporSources {
    private static final int WORLD_EMITTER_LIMIT = 2048;
    private static final double RANGE = 52.0;
    private static final long REFRESH_TICKS = 40;
    private static final Map<ServerLevel, LevelSources> LEVELS = new IdentityHashMap<>();
    private static final Comparator<VaporNetwork.Emitter> EMITTER_ORDER = Comparator
            .comparing(VaporNetwork.Emitter::id).thenComparing(VaporNetwork.Emitter::position);

    private record ActiveEmitter(VaporNetwork.Emitter emitter, long expiresAt) {}
    private record Sent(List<VaporNetwork.Emitter> emitters, long nextRefresh) {}
    private record ModernSent(Map<Key, VaporNetwork.Emitter> sources, int revision) {}

    private VaporSources() {
    }

    static boolean pulse(ServerLevel level, VaporNetwork.Pulse pulse) {
        if (level.isOutsideBuildHeight(pulse.position())) return false;
        LevelSources sources = LEVELS.computeIfAbsent(level, unused -> new LevelSources());
        if (sources.pulses.size() == VaporNetwork.MAX_PULSES_PER_TICK) return false;
        sources.pulses.add(pulse);
        return true;
    }

    static boolean set(ServerLevel level, VaporNetwork.Emitter emitter) {
        return set(level, emitter.position(), emitter);
    }

    static boolean set(ServerLevel level, BlockPos anchor, VaporNetwork.Emitter emitter) {
        return set(level, anchor, emitter, Long.MAX_VALUE);
    }

    /** A machine ticker refreshes the source each active tick. Stopped and unloaded machines expire automatically. */
    static void refresh(ServerLevel level, BlockPos anchor, VaporNetwork.Emitter emitter) {
        refresh(level, anchor, emitter, 1);
    }

    static void refresh(ServerLevel level, BlockPos anchor, VaporNetwork.Emitter emitter, int ticks) {
        set(level, anchor, emitter, level.getGameTime() + ticks);
    }

    private static boolean set(ServerLevel level, BlockPos anchor, VaporNetwork.Emitter emitter, long expiresAt) {
        if (level.isOutsideBuildHeight(emitter.position())) return false;
        LevelSources sources = LEVELS.computeIfAbsent(level, unused -> new LevelSources());
        Key key = new Key(emitter.id(), anchor);
        if (sources.emitters.size() == WORLD_EMITTER_LIMIT && !sources.emitters.containsKey(key)) return false;
        sources.emitters.put(key, new ActiveEmitter(emitter, expiresAt));
        return true;
    }

    static boolean stop(ServerLevel level, ResourceLocation id, BlockPos position) {
        LevelSources sources = LEVELS.get(level);
        return sources != null && sources.emitters.remove(new Key(id, position)) != null;
    }

    static void stopInChunk(ServerLevel level, ResourceLocation id, ChunkPos chunk) {
        var sources = LEVELS.get(level);
        if (sources != null) sources.emitters.keySet().removeIf(key -> key.provider().equals(id)
                && (key.anchor().getX() >> 4) == chunk.x && (key.anchor().getZ() >> 4) == chunk.z);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) LEVELS.remove(level);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        LevelSources sources = LEVELS.get(level);
        if (sources == null) return;
        sources.emitters.values().removeIf(emitter -> emitter.expiresAt <= level.getGameTime());
        sources.dispatch(level);
        if (sources.emitters.isEmpty() && sources.pulses.isEmpty() && sources.sent.isEmpty() && sources.modern.isEmpty()) LEVELS.remove(level);
    }

    private static final class LevelSources {
        private final Map<Key, ActiveEmitter> emitters = new HashMap<>();
        private final List<VaporNetwork.Pulse> pulses = new ArrayList<>();
        private final Map<ServerPlayer, Sent> sent = new IdentityHashMap<>();
        private final Map<ServerPlayer, ModernSent> modern = new IdentityHashMap<>();

        private void dispatch(ServerLevel level) {
            if (pulses.isEmpty() && (level.getGameTime() & 3) != 0) return;
            Set<ServerPlayer> present = new HashSet<>();
            for (ServerPlayer player : level.players()) {
                if (!VaporNetwork.available(player) || !FieldNetwork.interested(player, FieldNetwork.VAPOR)) continue;
                present.add(player);
                sendTo(level, player);
            }
            sent.keySet().retainAll(present);
            modern.keySet().retainAll(present);
            pulses.clear();
        }

        private void sendTo(ServerLevel level, ServerPlayer player) {
            boolean ownsVisuals = FieldNetwork.supportsLocalSources(player);
            var selected = emitters.entrySet().stream()
                    .filter(entry -> !ownsVisuals || !VaporDemonstrators.clientOwned(level, entry.getKey()))
                    .filter(entry -> visible(level, player, entry.getValue().emitter.position()))
                    .sorted(Comparator.<Map.Entry<Key, ActiveEmitter>>comparingDouble(entry ->
                                    entry.getValue().emitter.position().distToCenterSqr(player.position()))
                            .thenComparing(entry -> entry.getValue().emitter, EMITTER_ORDER)
                            .thenComparing(entry -> entry.getKey().anchor()))
                    .limit(VaporNetwork.MAX_EMITTERS_PER_CLIENT)
                    .toList();
            List<VaporNetwork.Pulse> nearbyPulses = pulses.stream()
                    .filter(pulse -> pulse.position().closerToCenterThan(player.position(), RANGE + pulse.radius()))
                    .limit(VaporNetwork.MAX_PULSES_PER_TICK)
                    .toList();
            if (ownsVisuals) {
                var current = new LinkedHashMap<Key, VaporNetwork.Emitter>();
                for (var entry : selected) current.put(entry.getKey(), entry.getValue().emitter);
                sendChanges(level, player, current, nearbyPulses);
            } else {
                var nearby = selected.stream().map(entry -> entry.getValue().emitter).sorted(EMITTER_ORDER).toList();
                sendLegacy(level, player, nearby, nearbyPulses);
            }
        }

        private void sendChanges(ServerLevel level, ServerPlayer player, Map<Key, VaporNetwork.Emitter> current,
                                 List<VaporNetwork.Pulse> pulses) {
            var sent = modern.get(player);
            int revision = FieldNetwork.revision(player, FieldNetwork.VAPOR);
            boolean initial = sent == null || sent.revision != revision;
            Map<Key, VaporNetwork.Emitter> previous = initial ? Map.of() : sent.sources;
            var removed = previous.keySet().stream().filter(key -> !current.containsKey(key)).toList();
            var changed = new ArrayList<VaporState.Source>();
            current.forEach((key, source) -> {
                if (!source.equals(previous.get(key))) changed.add(new VaporState.Source(key, source));
            });
            if (removed.isEmpty() && changed.isEmpty() && pulses.isEmpty()) return;
            FieldNetwork.sendVapor(player, new VaporState(level.dimension().location(), initial, changed, removed, pulses));
            if (current.isEmpty()) modern.remove(player);
            else modern.put(player, new ModernSent(current, revision));
        }

        private void sendLegacy(ServerLevel level, ServerPlayer player, List<VaporNetwork.Emitter> nearby,
                                List<VaporNetwork.Pulse> pulses) {
            Sent previous = sent.get(player);
            long now = level.getGameTime();
            boolean changed = previous == null ? !nearby.isEmpty() : !nearby.equals(previous.emitters);
            boolean refresh = previous != null && now >= previous.nextRefresh;
            if (changed || refresh || !pulses.isEmpty()) {
                VaporNetwork.send(player, new VaporNetwork.Frame(level.dimension().location(), nearby, pulses));
                if (nearby.isEmpty()) sent.remove(player);
                else sent.put(player, new Sent(nearby, now + REFRESH_TICKS));
            }
        }

        private static boolean visible(ServerLevel level, ServerPlayer observer, BlockPos pos) {
            return pos.distToCenterSqr(observer.position()) <= RANGE * RANGE && level.hasChunkAt(pos);
        }
    }
}

package art.arcane.auram.vapor;

import art.arcane.auram.Auram;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
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
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Auram.MODID)
public final class VaporSources {
    private static final int WORLD_EMITTER_LIMIT = 2048;
    private static final double RANGE = 52.0;
    private static final long REFRESH_TICKS = 40;
    private static final float ENTITY_PUFF_DENSITY = 0.25f;
    private static final Map<ServerLevel, LevelSources> LEVELS = new IdentityHashMap<>();
    private static final Comparator<VaporNetwork.Emitter> EMITTER_ORDER = Comparator
            .comparing(VaporNetwork.Emitter::id).thenComparing(VaporNetwork.Emitter::position);

    private record Key(ResourceLocation id, BlockPos pos) {}
    private record Sent(List<VaporNetwork.Emitter> emitters, long nextRefresh) {}
    private record ScheduledPuff(VaporNetwork.Pulse pulse, long due) {}

    private VaporSources() {
    }

    static boolean pulse(ServerLevel level, VaporNetwork.Pulse pulse) {
        if (level.isOutsideBuildHeight(pulse.position())) return false;
        LevelSources sources = LEVELS.computeIfAbsent(level, unused -> new LevelSources());
        if (sources.pulses.size() == VaporNetwork.MAX_PULSES_PER_TICK) return false;
        sources.pulses.add(pulse);
        return true;
    }

    static void entityPuff(ServerLevel level, BlockPos pos, int delay) {
        if (level.isOutsideBuildHeight(pos)) return;
        LevelSources sources = LEVELS.computeIfAbsent(level, unused -> new LevelSources());
        if (sources.entityPuffs.size() < VaporNetwork.MAX_PULSES_PER_TICK) {
            sources.entityPuffs.add(new ScheduledPuff(new VaporNetwork.Pulse(pos.immutable(), ENTITY_PUFF_DENSITY, 0),
                    level.getGameTime() + delay));
        }
    }

    static void trackTeleport(ServerLevel level, Entity entity) {
        LevelSources sources = LEVELS.computeIfAbsent(level, unused -> new LevelSources());
        if (sources.teleports.size() < VaporNetwork.MAX_PULSES_PER_TICK / 2) {
            sources.teleports.putIfAbsent(entity, entity.blockPosition());
        }
    }

    static boolean set(ServerLevel level, VaporNetwork.Emitter emitter) {
        return set(level, emitter.position(), emitter);
    }

    static boolean set(ServerLevel level, BlockPos anchor, VaporNetwork.Emitter emitter) {
        if (level.isOutsideBuildHeight(emitter.position())) return false;
        LevelSources sources = LEVELS.computeIfAbsent(level, unused -> new LevelSources());
        Key key = new Key(emitter.id(), anchor);
        if (sources.emitters.size() == WORLD_EMITTER_LIMIT && !sources.emitters.containsKey(key)) return false;
        sources.emitters.put(key, emitter);
        return true;
    }

    static boolean stop(ServerLevel level, ResourceLocation id, BlockPos position) {
        LevelSources sources = LEVELS.get(level);
        return sources != null && sources.emitters.remove(new Key(id, position)) != null;
    }

    static void stopInChunk(ServerLevel level, ResourceLocation id, ChunkPos chunk) {
        var sources = LEVELS.get(level);
        if (sources != null) sources.emitters.keySet().removeIf(key -> key.id.equals(id)
                && (key.pos.getX() >> 4) == chunk.x && (key.pos.getZ() >> 4) == chunk.z);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) LEVELS.remove(level);
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        LevelSources sources = LEVELS.get(level);
        if (sources == null) return;
        sources.dispatch(level);
        if (sources.emitters.isEmpty() && sources.pulses.isEmpty() && sources.entityPuffs.isEmpty()
                && sources.teleports.isEmpty() && sources.sent.isEmpty()) LEVELS.remove(level);
    }

    private static final class LevelSources {
        private final Map<Key, VaporNetwork.Emitter> emitters = new HashMap<>();
        private final List<VaporNetwork.Pulse> pulses = new ArrayList<>();
        private final List<ScheduledPuff> entityPuffs = new ArrayList<>();
        private final Map<Entity, BlockPos> teleports = new LinkedHashMap<>();
        private final Map<UUID, Sent> sent = new HashMap<>();

        private void dispatch(ServerLevel level) {
            finishTeleports(level);
            long now = level.getGameTime();
            List<VaporNetwork.Pulse> ready = pulses;
            if (!entityPuffs.isEmpty()) {
                ready = new ArrayList<>(pulses);
                var queued = entityPuffs.iterator();
                while (queued.hasNext()) {
                    ScheduledPuff puff = queued.next();
                    if (puff.due <= now) {
                        ready.add(puff.pulse);
                        queued.remove();
                    }
                }
            }
            if (ready.isEmpty() && (now & 3) != 0) return;
            Set<UUID> present = new HashSet<>();
            for (ServerPlayer player : level.players()) {
                if (!VaporNetwork.available(player)) continue;
                present.add(player.getUUID());
                sendTo(level, player, ready);
            }
            sent.keySet().retainAll(present);
            pulses.clear();
        }

        private void finishTeleports(ServerLevel level) {
            for (var teleport : teleports.entrySet()) {
                Entity entity = teleport.getKey();
                BlockPos from = teleport.getValue();
                // Across dimensions the level join and leave events already handle both ends.
                if (!entity.isRemoved() && entity.level() == level && !from.equals(entity.blockPosition())) {
                    entityPuff(level, from, 0);
                    entityPuff(level, entity.blockPosition(), 0);
                }
            }
            teleports.clear();
        }

        private void sendTo(ServerLevel level, ServerPlayer player, List<VaporNetwork.Pulse> ready) {
            List<VaporNetwork.Emitter> nearby = emitters.values().stream()
                    .filter(emitter -> emitter.position().distToCenterSqr(player.position()) <= RANGE * RANGE
                            && level.hasChunkAt(emitter.position()))
                    .sorted(Comparator.<VaporNetwork.Emitter>comparingDouble(emitter -> emitter.position()
                            .distToCenterSqr(player.position())).thenComparing(EMITTER_ORDER))
                    .limit(VaporNetwork.MAX_EMITTERS_PER_CLIENT)
                    .sorted(EMITTER_ORDER)
                    .toList();
            List<VaporNetwork.Pulse> nearbyPulses = ready.stream()
                    .filter(pulse -> pulse.position().closerToCenterThan(player.position(), RANGE + pulse.radius()))
                    .limit(VaporNetwork.MAX_PULSES_PER_TICK)
                    .toList();
            UUID id = player.getUUID();
            Sent previous = sent.get(id);
            long now = level.getGameTime();
            boolean changed = previous == null ? !nearby.isEmpty() : !nearby.equals(previous.emitters);
            boolean refresh = previous != null && now >= previous.nextRefresh;
            if (changed || refresh || !nearbyPulses.isEmpty()) {
                VaporNetwork.send(player, new VaporNetwork.Frame(level.dimension().location(), nearby, nearbyPulses));
                if (nearby.isEmpty()) sent.remove(id);
                else sent.put(id, new Sent(nearby, now + REFRESH_TICKS));
            }
        }
    }
}

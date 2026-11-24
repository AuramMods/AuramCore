package art.arcane.auram.wind;

import art.arcane.auram.Auram;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static art.arcane.auram.wind.WindNetwork.*;

@Mod.EventBusSubscriber(modid = Auram.MODID)
public final class WindSources {
    private static final int MAX_LEVEL_EMITTERS = 4096;
    private static final int REFRESH_TICKS = 40;
    private static final Comparator<Emitter> ORDER = Comparator.comparing(Emitter::id)
            .thenComparing(emitter -> emitter.flow().position());
    private static final Map<ServerLevel, LevelSources> LEVELS = new HashMap<>();

    private record Key(ResourceLocation id, BlockPos position) {}
    private record Sent(List<Emitter> emitters, long refreshAt) {}

    private WindSources() {}

    static boolean gust(ServerLevel level, Flow gust) {
        LevelSources sources = LEVELS.computeIfAbsent(level, ignored -> new LevelSources());
        if (sources.gusts.size() >= MAX_GUSTS_PER_TICK) return false;
        return sources.gusts.add(gust);
    }

    static boolean set(ServerLevel level, Emitter emitter) {
        LevelSources sources = LEVELS.computeIfAbsent(level, ignored -> new LevelSources());
        Key key = new Key(emitter.id(), emitter.flow().position());
        if (sources.emitters.size() >= MAX_LEVEL_EMITTERS && !sources.emitters.containsKey(key)) return false;
        if (!emitter.equals(sources.emitters.put(key, emitter))) sources.dirty = true;
        return true;
    }

    static boolean stop(ServerLevel level, ResourceLocation id, BlockPos position) {
        LevelSources sources = LEVELS.get(level);
        if (sources == null || sources.emitters.remove(new Key(id, position)) == null) return false;
        sources.dirty = true;
        return true;
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) LEVELS.remove(level);
    }

    @SubscribeEvent
    public static void unloadChunk(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        LevelSources sources = LEVELS.get(level);
        if (sources == null) return;
        var pos = event.getChunk().getPos();
        sources.dirty |= sources.emitters.keySet().removeIf(key ->
                (key.position.getX() >> 4) == pos.x && (key.position.getZ() >> 4) == pos.z);
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        LevelSources sources = LEVELS.get(level);
        if (sources == null) return;
        sources.dispatch(level);
        if (sources.emitters.isEmpty() && sources.gusts.isEmpty() && sources.sent.isEmpty()) LEVELS.remove(level);
    }

    private static boolean nearby(BlockPos position, BlockPos player) {
        // A section-aligned 64-block window reaches farther on one side when the player is near a section edge.
        return Math.abs(position.getX() - player.getX()) <= 48 && Math.abs(position.getY() - player.getY()) <= 48
                && Math.abs(position.getZ() - player.getZ()) <= 48;
    }

    private static final class LevelSources {
        private final Map<Key, Emitter> emitters = new HashMap<>();
        private final List<Flow> gusts = new ArrayList<>();
        private final Map<UUID, Sent> sent = new HashMap<>();
        private boolean dirty;

        private void dispatch(ServerLevel level) {
            long ticks = level.getGameTime();
            if (!dirty && gusts.isEmpty() && (ticks & 3) != 0) return;
            Set<UUID> present = new HashSet<>();
            for (ServerPlayer player : level.players()) {
                if (!available(player)) continue;
                UUID playerId = player.getUUID();
                present.add(playerId);
                BlockPos origin = player.blockPosition();
                List<Emitter> selected = emitters.values().stream()
                        .filter(emitter -> nearby(emitter.flow().position(), origin)
                                && level.hasChunkAt(emitter.flow().position()))
                        .sorted(Comparator.<Emitter>comparingDouble(emitter ->
                                emitter.flow().position().distToCenterSqr(player.position())).thenComparing(ORDER))
                        .limit(MAX_EMITTERS_PER_CLIENT).sorted(ORDER).toList();
                List<Flow> nearbyGusts = gusts.stream().filter(gust -> nearby(gust.position(), origin)).toList();
                Sent previous = sent.get(playerId);
                boolean changed = previous == null ? !selected.isEmpty() : !selected.equals(previous.emitters);
                if (changed || !nearbyGusts.isEmpty() || (previous != null && ticks >= previous.refreshAt)) {
                    send(player, new Frame(level.dimension().location(), selected, nearbyGusts));
                    if (selected.isEmpty()) sent.remove(playerId);
                    else sent.put(playerId, new Sent(selected, ticks + REFRESH_TICKS));
                }
            }
            sent.keySet().retainAll(present);
            gusts.clear();
            dirty = false;
        }
    }
}

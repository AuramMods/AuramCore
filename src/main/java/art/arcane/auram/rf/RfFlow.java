package art.arcane.auram.rf;

import art.arcane.auram.Auram;
import art.arcane.auram.vapor.MachineVapor;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;

@Mod.EventBusSubscriber(modid = Auram.MODID)
public final class RfFlow {
    private static final int SAMPLE_TICKS = 4;
    private static final int RANGE = 48;
    private static final boolean PROBE = Boolean.getBoolean("auram.rf.probe");
    private static final IntConsumer DISCARD = amount -> {};
    private static final Map<ServerLevel, LevelFlow> LEVELS = new HashMap<>();

    /** Canonical block-to-neighbor edge in the positive X, Y or Z direction. */
    public record Edge(long position, int axis) {
        public Edge {
            if (axis < 0 || axis > 2) throw new IllegalArgumentException("RF edge axis outside 0–2");
        }
    }

    public record Sample(Edge edge, long forward, long backward) {}

    private record Hop(Edge edge, boolean forward) {}

    private static final class Counter {
        long forward;
        long backward;

        void add(boolean direction, long amount) {
            if (direction) forward = sum(forward, amount);
            else backward = sum(backward, amount);
        }
    }

    private static final class Route {
        final List<Hop> hops;
        final long destination;
        long accepted;

        Route(List<Hop> hops, long destination) {
            this.hops = hops;
            this.destination = destination;
        }
    }

    private static final class MachineInput {
        long routed;
        long received;
    }

    private static final class LevelFlow {
        final List<Route> pending = new ArrayList<>();
        final Map<Edge, Counter> edges = new HashMap<>();
        final Map<Long, MachineInput> machineInputs = new HashMap<>();
        final Map<Long, Long> sampledInputs = new HashMap<>();
        final Set<UUID> sent = new HashSet<>();
    }

    private RfFlow() {}

    /**
     * Creates a server-thread observer for one ordered route of adjacent world positions.
     * Report the result of committed transfers with the returned consumer.
     */
    public static IntConsumer observeRoute(ServerLevel level, long[] positions) {
        if (positions.length < 2) return DISCARD;
        var hops = new ArrayList<Hop>(positions.length - 1);
        BlockPos previous = BlockPos.of(positions[0]);
        for (int i = 1; i < positions.length; i++) {
            BlockPos next = BlockPos.of(positions[i]);
            int dx = next.getX() - previous.getX();
            int dy = next.getY() - previous.getY();
            int dz = next.getZ() - previous.getZ();
            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) return DISCARD;
            int axis = dx != 0 ? 0 : dy != 0 ? 1 : 2;
            boolean forward = dx + dy + dz > 0;
            hops.add(new Hop(new Edge((forward ? previous : next).asLong(), axis), forward));
            previous = next;
        }

        LevelFlow flow = LEVELS.computeIfAbsent(level, ignored -> new LevelFlow());
        var route = new Route(List.copyOf(hops), positions[positions.length - 1]);
        return amount -> {
            if (amount <= 0) return;
            if (route.accepted == 0) flow.pending.add(route);
            route.accepted = sum(route.accepted, amount);
        };
    }

    /** Reports RF accepted by a machine's input during a committed transfer. */
    public static void recordAcceptedInput(BlockEntity machine, long amount) {
        if (amount <= 0 || machine.isRemoved() || !(machine.getLevel() instanceof ServerLevel level)
                || !level.getServer().isSameThread()) return;
        var input = LEVELS.computeIfAbsent(level, ignored -> new LevelFlow()).machineInputs
                .computeIfAbsent(machine.getBlockPos().asLong(), ignored -> new MachineInput());
        input.received = sum(input.received, amount);
    }

    @SubscribeEvent
    public static void levelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        LevelFlow flow = LEVELS.get(level);
        if (flow == null) return;

        for (Route route : flow.pending) {
            for (Hop hop : route.hops) {
                flow.edges.computeIfAbsent(hop.edge, ignored -> new Counter()).add(hop.forward, route.accepted);
            }
            var input = flow.machineInputs.computeIfAbsent(route.destination, ignored -> new MachineInput());
            input.routed = sum(input.routed, route.accepted);
            route.accepted = 0;
        }
        flow.pending.clear();
        // A receive hook can observe the same delivery as the route observer.
        flow.machineInputs.forEach((pos, input) -> flow.sampledInputs.merge(pos,
                Math.max(input.routed, input.received), RfFlow::sum));
        flow.machineInputs.clear();
        if ((level.getGameTime() % SAMPLE_TICKS) == 0) {
            flow.sampledInputs.forEach((pos, amount) ->
                    MachineVapor.rfInput(level, BlockPos.of(pos), RfPower.vaporRate(average(amount)), SAMPLE_TICKS));
            flow.sampledInputs.clear();
            publish(level, flow);
        }
    }

    private static void publish(ServerLevel level, LevelFlow flow) {
        if (flow.edges.isEmpty() && flow.sent.isEmpty()) return;
        var samples = new ArrayList<Sample>(flow.edges.size());
        flow.edges.forEach((edge, count) -> samples.add(new Sample(edge,
                average(count.forward), average(count.backward))));
        flow.edges.clear();

        Set<UUID> next = new HashSet<>();
        for (var player : level.players()) {
            if (!RfNetwork.available(player)) continue;
            BlockPos center = player.getCamera().blockPosition();
            var nearby = samples.stream()
                    .filter(sample -> inRange(sample.edge, center))
                    .sorted(Comparator.<Sample>comparingDouble(sample ->
                                    BlockPos.of(sample.edge.position).distSqr(center))
                            .thenComparingLong(sample -> sample.edge.position)
                            .thenComparingInt(sample -> sample.edge.axis))
                    .limit(RfNetwork.MAX_EDGES).toList();
            UUID playerId = player.getUUID();
            if (!nearby.isEmpty()) next.add(playerId);
            if (!nearby.isEmpty() || flow.sent.contains(playerId)) {
                RfNetwork.send(player, new RfNetwork.Frame(level.dimension().location(), nearby));
                if (PROBE && !nearby.isEmpty()) {
                    long peak = nearby.stream().mapToLong(sample -> Math.max(sample.forward, sample.backward)).max().orElse(0);
                    LogUtils.getLogger().info("RF field sent {} edges, peak {} RF/t", nearby.size(), peak);
                }
            }
        }
        flow.sent.clear();
        flow.sent.addAll(next);
    }

    private static boolean inRange(Edge edge, BlockPos center) {
        BlockPos pos = BlockPos.of(edge.position);
        return Math.abs(pos.getX() - center.getX()) <= RANGE && Math.abs(pos.getY() - center.getY()) <= RANGE
                && Math.abs(pos.getZ() - center.getZ()) <= RANGE;
    }

    private static long average(long amount) {
        return amount == 0 ? 0 : 1 + (amount - 1) / SAMPLE_TICKS;
    }

    private static long sum(long lhs, long rhs) {
        return lhs + Math.min(rhs, Long.MAX_VALUE - lhs);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) LEVELS.remove(level);
    }
}

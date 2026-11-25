package art.arcane.auram.rf.mekanism;

import art.arcane.auram.rf.RfFlow;
import mekanism.api.math.FloatingLong;
import mekanism.common.content.network.EnergyNetwork;
import mekanism.common.content.network.transmitter.Transmitter;
import mekanism.common.content.network.transmitter.UniversalCable;
import mekanism.common.tile.transmitter.TileEntityUniversalCable;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.LongConsumer;

/** Observes Mekanism's shared buffer and projects deliveries onto connected cable segments. */
public final class MekanismRf {
    private static final int MAX_INPUTS = 64;
    private static final int MAX_ROUTES = 128;
    private static final int MAX_VISITED = 4096;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<EnergyNetwork, Flow> NETWORKS = new WeakHashMap<>();

    private MekanismRf() {}

    record Endpoint(BlockPos cable, BlockPos machine) {
        static Endpoint from(BlockPos cable, Direction side) {
            return new Endpoint(cable, cable.relative(side));
        }
    }

    private record Route(Endpoint input, Endpoint output) {}

    private static final class Flow {
        private final Map<Endpoint, FloatingLong> inputs = new LinkedHashMap<>();
        private final Map<Route, Path> paths = new LinkedHashMap<>();
    }

    private record Path(long[] positions, LongConsumer observe) {}

    public static void inserted(UniversalCable cable, Direction side, FloatingLong amount) {
        if (amount.isZero() || side == null || !cable.hasTransmitterNetwork()
                || !(cable.getTileWorld() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        var flow = NETWORKS.computeIfAbsent(cable.getTransmitterNetwork(), unused -> new Flow());
        Endpoint input = Endpoint.from(cable.getTilePos(), side);
        if (flow.inputs.size() < MAX_INPUTS || flow.inputs.containsKey(input)) {
            flow.inputs.merge(input, amount.copy(), FloatingLong::add);
        }
    }

    public static void extracted(UniversalCable cable, Direction side, FloatingLong amount) {
        if (amount.isZero() || side == null || !cable.hasTransmitterNetwork()
                || !(cable.getTileWorld() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        EnergyNetwork network = cable.getTransmitterNetwork();
        delivered(network, Endpoint.from(cable.getTilePos(), side), amount);
        if (network.getBuffer().isZero()) drained(network);
    }

    public static void drained(EnergyNetwork network) {
        Flow flow = NETWORKS.get(network);
        if (flow != null) flow.inputs.clear();
    }

    static void delivered(EnergyNetwork network, @Nullable Endpoint output, FloatingLong amount) {
        if (amount.isZero() || !(network.getWorld() instanceof ServerLevel level)
                || !level.getServer().isSameThread()) return;
        Flow flow = NETWORKS.get(network);
        boolean route = output != null && RfFlow.needsRoutes(level);
        if (output != null && !route) {
            RfFlow.recordRoutedInput(level, output.machine.asLong(), EnergyUnit.FORGE_ENERGY.convertToAsLong(amount));
        }
        FloatingLong unrouted = FloatingLong.ZERO;
        if (flow != null) {
            var entries = flow.inputs.entrySet().iterator();
            while (!amount.isZero() && entries.hasNext()) {
                var entry = entries.next();
                Endpoint input = entry.getKey();
                FloatingLong stored = entry.getValue();
                FloatingLong used = stored.min(amount);
                amount = amount.subtract(used);
                if (used.equals(stored)) entries.remove();
                else entry.setValue(stored.subtract(used));

                if (!route) continue;
                Path path = path(level, network, flow, new Route(input, output));
                if (path == null) unrouted = unrouted.add(used);
                else path.observe.accept(EnergyUnit.FORGE_ENERGY.convertToAsLong(used));
            }
        }
        if (!route) return;
        long remainder = EnergyUnit.FORGE_ENERGY.convertToAsLong(unrouted.add(amount));
        if (remainder > 0) {
            // A persisted or merged buffer can deliver energy with no observable source.
            RfFlow.observeLongRoute(level, new long[]{output.cable.asLong(), output.machine.asLong()})
                    .accept(remainder);
        }
    }

    private static Path path(ServerLevel level, EnergyNetwork network, Flow flow, Route route) {
        Path cached = flow.paths.get(route);
        if (cached != null && valid(level, network, cached.positions)) return cached;
        flow.paths.remove(route);
        long[] positions = findPath(level, network, route);
        if (positions == null) return null;
        Path found = new Path(positions, RfFlow.observeLongRoute(level, positions));
        if (flow.paths.size() == MAX_ROUTES) {
            Iterator<Route> oldest = flow.paths.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        flow.paths.put(route, found);
        return found;
    }

    private static long[] findPath(ServerLevel level, EnergyNetwork network, Route route) {
        BlockPos start = route.input.cable;
        BlockPos end = route.output.cable;
        if (route.input.machine.equals(route.output.machine) || cable(level, network, start) == null) return null;
        var queue = new ArrayDeque<BlockPos>();
        var previous = new HashMap<BlockPos, BlockPos>();
        queue.add(start);
        previous.put(start, start);
        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();
            if (current.equals(end)) {
                var cablePositions = new ArrayDeque<BlockPos>();
                for (BlockPos node = end; !node.equals(start); node = previous.get(node)) cablePositions.addFirst(node);
                cablePositions.addFirst(start);
                long[] result = new long[cablePositions.size() + 2];
                result[0] = route.input.machine.asLong();
                int index = 1;
                for (BlockPos node : cablePositions) result[index++] = node.asLong();
                result[index] = route.output.machine.asLong();
                return result;
            }
            UniversalCable from = cable(level, network, current);
            if (from == null) continue;
            for (Direction side : DIRECTIONS) {
                if (previous.size() >= MAX_VISITED || !connected(from, side)) continue;
                BlockPos next = current.relative(side);
                if (previous.containsKey(next)) continue;
                UniversalCable to = cable(level, network, next);
                if (to != null && connected(to, side.getOpposite())) {
                    previous.put(next, current);
                    queue.add(next);
                }
            }
        }
        return null;
    }

    private static boolean valid(ServerLevel level, EnergyNetwork network, long[] route) {
        UniversalCable previous = cable(level, network, BlockPos.of(route[1]));
        if (previous == null) return false;
        for (int i = 2; i < route.length - 1; i++) {
            BlockPos pos = BlockPos.of(route[i]);
            UniversalCable next = cable(level, network, pos);
            BlockPos from = previous.getTilePos();
            Direction side = Direction.fromDelta(pos.getX() - from.getX(), pos.getY() - from.getY(), pos.getZ() - from.getZ());
            if (next == null || side == null || !connected(previous, side) || !connected(next, side.getOpposite())) return false;
            previous = next;
        }
        return true;
    }

    private static UniversalCable cable(ServerLevel level, EnergyNetwork network, BlockPos position) {
        if (!level.hasChunkAt(position) || !(level.getBlockEntity(position) instanceof TileEntityUniversalCable tile)) return null;
        UniversalCable cable = tile.getTransmitter();
        return cable.getTransmitterNetwork() == network ? cable : null;
    }

    private static boolean connected(UniversalCable cable, Direction side) {
        return Transmitter.connectionMapContainsSide(cable.currentTransmitterConnections, side);
    }
}

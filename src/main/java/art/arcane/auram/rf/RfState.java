package art.arcane.auram.rf;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** A snapshot or a change to an existing RF view, including empty heartbeat messages. */
public record RfState(ResourceLocation dimension, boolean initial, List<RfFlow.Sample> changed,
                      List<RfFlow.Edge> removed) {
    public RfState {
        changed = List.copyOf(changed);
        removed = List.copyOf(removed);
    }

    public static void encode(RfState state, FriendlyByteBuf out) {
        out.writeResourceLocation(state.dimension);
        out.writeBoolean(state.initial);
        out.writeVarInt(state.changed.size());
        for (var sample : state.changed) {
            writeEdge(out, sample.edge());
            out.writeVarLong(sample.forward()).writeVarLong(sample.backward());
        }
        out.writeVarInt(state.removed.size());
        for (var edge : state.removed) writeEdge(out, edge);
    }

    public static RfState decode(FriendlyByteBuf in) {
        ResourceLocation dimension = in.readResourceLocation();
        boolean initial = in.readBoolean();
        int changes = count(in);
        var changed = new ArrayList<RfFlow.Sample>(changes);
        for (int i = 0; i < changes; i++) {
            var edge = readEdge(in);
            long forward = in.readVarLong();
            long backward = in.readVarLong();
            if (forward < 0 || backward < 0) throw new IllegalArgumentException("Negative RF rate");
            changed.add(new RfFlow.Sample(edge, forward, backward));
        }
        int removals = count(in);
        var removed = new ArrayList<RfFlow.Edge>(removals);
        for (int i = 0; i < removals; i++) removed.add(readEdge(in));
        return new RfState(dimension, initial, changed, removed);
    }

    private static void writeEdge(FriendlyByteBuf out, RfFlow.Edge edge) {
        out.writeLong(edge.position()).writeByte(edge.axis());
    }

    private static RfFlow.Edge readEdge(FriendlyByteBuf in) {
        return new RfFlow.Edge(in.readLong(), in.readUnsignedByte());
    }

    private static int count(FriendlyByteBuf in) {
        int count = in.readVarInt();
        if (count < 0 || count > RfNetwork.MAX_EDGES) throw new IllegalArgumentException("Invalid RF update size");
        return count;
    }
}

package art.arcane.auram.vapor;

import art.arcane.auram.field.ClientFieldSources.Key;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Sparse updates to continuous sources whose activity is known only on the server. */
public record VaporState(ResourceLocation dimension, List<Source> changed, List<Key> removed,
                         List<VaporNetwork.Pulse> pulses) {
    public VaporState {
        changed = List.copyOf(changed);
        removed = List.copyOf(removed);
        pulses = List.copyOf(pulses);
    }

    public record Source(Key key, VaporNetwork.Emitter emitter) {}

    public static void encode(VaporState state, FriendlyByteBuf out) {
        out.writeResourceLocation(state.dimension);
        out.writeVarInt(state.changed.size());
        for (Source source : state.changed) {
            writeKey(out, source.key);
            var vent = source.emitter.position();
            out.writeBoolean(!vent.equals(source.key.anchor()));
            if (!vent.equals(source.key.anchor())) out.writeBlockPos(vent);
            out.writeFloat(source.emitter.rate());
        }
        out.writeVarInt(state.removed.size());
        for (Key key : state.removed) writeKey(out, key);
        out.writeVarInt(state.pulses.size());
        for (var pulse : state.pulses) {
            out.writeBlockPos(pulse.position());
            out.writeFloat(pulse.density());
            out.writeVarInt(pulse.radius());
        }
    }

    public static VaporState decode(FriendlyByteBuf in) {
        var dimension = in.readResourceLocation();
        var changes = read(in, VaporNetwork.MAX_EMITTERS_PER_CLIENT, buffer -> {
            var key = readKey(buffer);
            var vent = buffer.readBoolean() ? buffer.readBlockPos() : key.anchor();
            return new Source(key, new VaporNetwork.Emitter(key.provider(), vent, buffer.readFloat()));
        });
        var removals = read(in, VaporNetwork.MAX_EMITTERS_PER_CLIENT, VaporState::readKey);
        var pulses = read(in, VaporNetwork.MAX_PULSES_PER_TICK,
                buffer -> new VaporNetwork.Pulse(buffer.readBlockPos(), buffer.readFloat(), buffer.readVarInt()));
        return new VaporState(dimension, changes, removals, pulses);
    }

    private static void writeKey(FriendlyByteBuf buffer, Key key) {
        buffer.writeResourceLocation(key.provider());
        buffer.writeBlockPos(key.anchor());
    }

    private static Key readKey(FriendlyByteBuf buffer) {
        return new Key(buffer.readResourceLocation(), buffer.readBlockPos());
    }

    private static <T> List<T> read(FriendlyByteBuf in, int limit, Function<FriendlyByteBuf, T> element) {
        int length = in.readVarInt();
        if (length < 0 || length > limit) throw new IllegalArgumentException("Invalid vapor update length");
        var result = new ArrayList<T>(length);
        for (int i = 0; i < length; i++) result.add(element.apply(in));
        return result;
    }
}

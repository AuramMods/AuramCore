package art.arcane.auram.wind;

import art.arcane.auram.Auram;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public final class WindNetwork {
    static final int MAX_EMITTERS_PER_CLIENT = 128;
    static final int MAX_GUSTS_PER_TICK = 32;
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(Auram.MODID, "wind"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(WindNetwork::accepts)
            .serverAcceptedVersions(WindNetwork::accepts)
            .simpleChannel();

    public record Flow(BlockPos position, float x, float y, float z) {
        public Flow {
            position = position.immutable();
            float squared = x * x + y * y + z * z;
            if (!Float.isFinite(squared) || squared > 1.000001f) {
                throw new IllegalArgumentException("Wind magnitude must be between 0 and 1");
            }
        }
    }

    public record Emitter(ResourceLocation id, Flow flow) {}

    public record Frame(ResourceLocation dimension, List<Emitter> emitters, List<Flow> gusts) {
        public Frame {
            emitters = List.copyOf(emitters);
            gusts = List.copyOf(gusts);
        }
    }

    private static volatile Consumer<Frame> receiver = frame -> {};
    private WindNetwork() {
    }

    public static void setReceiver(Consumer<Frame> clientReceiver) {
        receiver = clientReceiver;
    }

    public static void register() {
        CHANNEL.messageBuilder(Frame.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WindNetwork::encode)
                .decoder(WindNetwork::decode)
                .consumerMainThread((frame, context) -> receiver.accept(frame))
                .add();
    }

    private static boolean accepts(String remote) {
        return VERSION.equals(remote) || NetworkRegistry.ABSENT.equals(remote) || NetworkRegistry.ACCEPTVANILLA.equals(remote);
    }

    static void encode(Frame frame, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(frame.dimension);
        buffer.writeVarInt(frame.emitters.size());
        for (Emitter emitter : frame.emitters) {
            buffer.writeResourceLocation(emitter.id);
            writeFlow(buffer, emitter.flow);
        }
        buffer.writeVarInt(frame.gusts.size());
        for (Flow gust : frame.gusts) writeFlow(buffer, gust);
    }

    private static void writeFlow(FriendlyByteBuf buffer, Flow flow) {
        buffer.writeBlockPos(flow.position);
        buffer.writeFloat(flow.x).writeFloat(flow.y).writeFloat(flow.z);
    }

    private static Flow readFlow(FriendlyByteBuf buffer) {
        return new Flow(buffer.readBlockPos(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    static Frame decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        List<Emitter> emitters = readList(buffer, MAX_EMITTERS_PER_CLIENT,
                in -> new Emitter(in.readResourceLocation(), readFlow(in)));
        return new Frame(dimension, emitters, readList(buffer, MAX_GUSTS_PER_TICK, WindNetwork::readFlow));
    }

    private static <T> List<T> readList(FriendlyByteBuf buffer, int limit, Function<FriendlyByteBuf, T> read) {
        int size = buffer.readVarInt();
        if (size < 0 || size > limit) throw new IllegalArgumentException("Too many Wind sources in packet");
        List<T> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(read.apply(buffer));
        return result;
    }

    static boolean available(ServerPlayer player) {
        var connection = player.connection.connection;
        return connection.isMemoryConnection() || CHANNEL.isRemotePresent(connection);
    }

    static void send(ServerPlayer player, Frame frame) {
        var connection = player.connection.connection;
        if (connection.isMemoryConnection()) {
            // The integrated server shares the client JVM, even without a negotiated channel.
            receiver.accept(frame);
        } else {
            CHANNEL.sendTo(frame, connection, NetworkDirection.PLAY_TO_CLIENT);
        }
    }
}

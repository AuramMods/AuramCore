package art.arcane.auram.rf;

import art.arcane.auram.Auram;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class RfNetwork {
    static final int MAX_EDGES = 512;
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(Auram.MODID, "rf_flow"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(RfNetwork::accepts)
            .serverAcceptedVersions(RfNetwork::accepts)
            .simpleChannel();

    public record Frame(ResourceLocation dimension, List<RfFlow.Sample> edges) {
        public Frame {
            edges = List.copyOf(edges);
        }
    }

    private static volatile Consumer<Frame> receiver = frame -> {};

    private RfNetwork() {}

    public static void setReceiver(Consumer<Frame> clientReceiver) {
        receiver = clientReceiver;
    }

    public static void register() {
        CHANNEL.messageBuilder(Frame.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RfNetwork::encode)
                .decoder(RfNetwork::decode)
                .consumerMainThread((frame, context) -> receiver.accept(frame))
                .add();
    }

    private static boolean accepts(String remote) {
        return VERSION.equals(remote) || NetworkRegistry.ABSENT.equals(remote) || NetworkRegistry.ACCEPTVANILLA.equals(remote);
    }

    private static void encode(Frame frame, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(frame.dimension);
        buffer.writeVarInt(frame.edges.size());
        for (var sample : frame.edges) {
            buffer.writeLong(sample.edge().position()).writeByte(sample.edge().axis());
            buffer.writeVarLong(sample.forward()).writeVarLong(sample.backward());
        }
    }

    private static Frame decode(FriendlyByteBuf buffer) {
        var dimension = buffer.readResourceLocation();
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_EDGES) throw new IllegalArgumentException("Invalid RF edge count");
        var edges = new ArrayList<RfFlow.Sample>(size);
        for (int i = 0; i < size; i++) {
            var edge = new RfFlow.Edge(buffer.readLong(), buffer.readUnsignedByte());
            long forwards = buffer.readVarLong();
            long backwards = buffer.readVarLong();
            if (forwards < 0 || backwards < 0) throw new IllegalArgumentException("Negative RF rate");
            edges.add(new RfFlow.Sample(edge, forwards, backwards));
        }
        return new Frame(dimension, edges);
    }

    static boolean available(ServerPlayer player) {
        var connection = player.connection.connection;
        return connection.isMemoryConnection() || CHANNEL.isRemotePresent(connection);
    }

    static void send(ServerPlayer player, Frame frame) {
        var connection = player.connection.connection;
        if (connection.isMemoryConnection()) receiver.accept(frame);
        else CHANNEL.sendTo(frame, connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}

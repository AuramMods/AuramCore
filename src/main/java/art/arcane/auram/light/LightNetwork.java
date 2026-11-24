package art.arcane.auram.light;

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

public final class LightNetwork {
    static final int MAX_PER_CLIENT = 128;
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(Auram.MODID, "light"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(LightNetwork::accepts)
            .serverAcceptedVersions(LightNetwork::accepts)
            .simpleChannel();

    record Frame(ResourceLocation dimension, List<LightSample> sources) {
        Frame {
            sources = List.copyOf(sources);
        }
    }

    private static volatile Consumer<Frame> receiver = frame -> {};

    private LightNetwork() {}

    static void setReceiver(Consumer<Frame> clientReceiver) {
        receiver = clientReceiver;
    }

    public static void register() {
        CHANNEL.messageBuilder(Frame.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LightNetwork::encode)
                .decoder(LightNetwork::decode)
                .consumerMainThread((frame, context) -> receiver.accept(frame))
                .add();
    }

    private static boolean accepts(String remote) {
        return VERSION.equals(remote) || NetworkRegistry.ABSENT.equals(remote) || NetworkRegistry.ACCEPTVANILLA.equals(remote);
    }

    static void encode(Frame frame, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(frame.dimension());
        buffer.writeVarInt(frame.sources().size());
        for (LightSample source : frame.sources()) {
            buffer.writeBlockPos(source.position());
            buffer.writeInt(source.rgb());
            buffer.writeFloat(source.strength());
            buffer.writeVarInt(source.duration());
            if (source.duration() != 0) buffer.writeVarLong(source.endsAt());
        }
    }

    static Frame decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_PER_CLIENT) throw new IllegalArgumentException("Too many light sources in frame");
        var sources = new ArrayList<LightSample>(count);
        for (int i = 0; i < count; i++) {
            var position = buffer.readBlockPos();
            int rgb = buffer.readInt();
            float strength = buffer.readFloat();
            int duration = buffer.readVarInt();
            if ((rgb & 0xff000000) != 0 || !Float.isFinite(strength) || strength < 0f || strength > 1f
                    || duration < 0 || duration > LightSample.MAX_PULSE_TICKS) {
                throw new IllegalArgumentException("Invalid light source");
            }
            long endsAt = duration == 0 ? 0 : buffer.readVarLong();
            sources.add(new LightSample(position, rgb, strength, endsAt, duration));
        }
        return new Frame(dimension, sources);
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

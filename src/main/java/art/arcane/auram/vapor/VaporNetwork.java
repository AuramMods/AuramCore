package art.arcane.auram.vapor;

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

public final class VaporNetwork {
    static final int MAX_EMITTERS_PER_CLIENT = 128;
    static final int MAX_PULSES_PER_TICK = 32;
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(Auram.MODID, "vapor"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(VaporNetwork::accepts)
            .serverAcceptedVersions(VaporNetwork::accepts)
            .simpleChannel();

    public record Emitter(ResourceLocation id, BlockPos position, float rate) {
        public Emitter {
            Vapor.validateDensity(rate);
        }
    }

    public record Pulse(BlockPos position, float density, int radius) {
        public Pulse {
            Vapor.validateDensity(density);
            if (radius < 0 || radius > Vapor.MAX_RADIUS) throw new IllegalArgumentException("Vapor radius out of range");
        }
    }

    public record Frame(ResourceLocation dimension, List<Emitter> emitters, List<Pulse> pulses) {
        public Frame {
            emitters = List.copyOf(emitters);
            pulses = List.copyOf(pulses);
        }
    }

    private static Consumer<Frame> receiver = frame -> {};
    private VaporNetwork() {
    }

    public static void setReceiver(Consumer<Frame> clientReceiver) {
        receiver = clientReceiver;
    }

    public static void register() {
        CHANNEL.messageBuilder(Frame.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VaporNetwork::encode)
                .decoder(VaporNetwork::decode)
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
            buffer.writeBlockPos(emitter.position);
            buffer.writeFloat(emitter.rate);
        }
        buffer.writeVarInt(frame.pulses.size());
        for (Pulse pulse : frame.pulses) {
            buffer.writeBlockPos(pulse.position);
            buffer.writeFloat(pulse.density);
            buffer.writeVarInt(pulse.radius);
        }
    }

    static Frame decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        List<Emitter> emitters = readList(buffer, MAX_EMITTERS_PER_CLIENT,
                in -> new Emitter(in.readResourceLocation(), in.readBlockPos(), in.readFloat()));
        List<Pulse> pulses = readList(buffer, MAX_PULSES_PER_TICK,
                in -> new Pulse(in.readBlockPos(), in.readFloat(), in.readVarInt()));
        return new Frame(dimension, emitters, pulses);
    }

    private static <T> List<T> readList(FriendlyByteBuf buffer, int limit, Function<FriendlyByteBuf, T> read) {
        int size = buffer.readVarInt();
        if (size < 0 || size > limit) throw new IllegalArgumentException("Too many Vapor sources in packet");
        List<T> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(read.apply(buffer));
        return result;
    }

    static boolean available(ServerPlayer player) {
        return CHANNEL.isRemotePresent(player.connection.connection);
    }

    static void send(ServerPlayer player, Frame frame) {
        CHANNEL.sendTo(frame, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}

package art.arcane.auram.field;

import art.arcane.auram.Auram;
import art.arcane.auram.rf.RfState;
import art.arcane.auram.vapor.VaporState;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Optional field replication channel. The presence of this channel identifies local source support. */
@Mod.EventBusSubscriber(modid = Auram.MODID)
public final class FieldNetwork {
    public static final int VAPOR = 1;
    public static final int RF = 2;
    public static final int LIGHT = 4;
    private static final int ALL = VAPOR | RF | LIGHT;
    private static final Map<Connection, Interest> INTEREST = new IdentityHashMap<>();
    private static final String VERSION = "1";
    private static final ResourceLocation NAME = ResourceLocation.fromNamespaceAndPath(Auram.MODID, "fields");
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(NAME)
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
            .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
            .simpleChannel();

    private static volatile Consumer<VaporState> vaporReceiver = state -> {};
    private static volatile Consumer<RfState> rfReceiver = state -> {};

    private record Subscription(int effects) {}
    private record Interest(int effects, int vapor, int rf, int light) {
        private static final Interest EMPTY = new Interest(0, 0, 0, 0);

        Interest update(int requested) {
            int changes = effects ^ requested;
            return new Interest(requested, vapor + ((changes & VAPOR) == 0 ? 0 : 1),
                    rf + ((changes & RF) == 0 ? 0 : 1), light + ((changes & LIGHT) == 0 ? 0 : 1));
        }
    }

    private FieldNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(VaporState.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VaporState::encode)
                .decoder(VaporState::decode)
                .consumerMainThread((state, context) -> vaporReceiver.accept(state))
                .add();
        CHANNEL.messageBuilder(RfState.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RfState::encode)
                .decoder(RfState::decode)
                .consumerMainThread((state, context) -> rfReceiver.accept(state))
                .add();
        CHANNEL.messageBuilder(Subscription.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder((request, buffer) -> buffer.writeByte(request.effects))
                .decoder(buffer -> new Subscription(buffer.readUnsignedByte()))
                .consumerMainThread((request, context) -> {
                    var sender = context.get().getSender();
                    if (sender != null) setInterest(sender, request.effects);
                }).add();
    }

    public static void setVaporReceiver(Consumer<VaporState> receiver) {
        vaporReceiver = receiver;
    }

    public static void sendVapor(ServerPlayer player, VaporState state) {
        var connection = player.connection.connection;
        if (connection.isMemoryConnection()) vaporReceiver.accept(state);
        else CHANNEL.sendTo(state, connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void setRfReceiver(Consumer<RfState> receiver) {
        rfReceiver = receiver;
    }

    public static void sendRf(ServerPlayer player, RfState state) {
        var connection = player.connection.connection;
        if (connection.isMemoryConnection()) rfReceiver.accept(state);
        else CHANNEL.sendTo(state, connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void subscribe(Connection connection, int effects) {
        if (hasNegotiatedVersion(connection)) {
            CHANNEL.sendTo(new Subscription(effects), connection, NetworkDirection.PLAY_TO_SERVER);
        }
    }

    public static void setInterest(ServerPlayer player, int effects) {
        var connection = player.connection.connection;
        var old = INTEREST.getOrDefault(connection, Interest.EMPTY);
        INTEREST.put(connection, old.update(effects & ALL));
    }

    public static boolean interested(ServerPlayer player, int effect) {
        return !supportsLocalSources(player) || (interest(player).effects & effect) != 0;
    }

    public static int revision(ServerPlayer player, int effect) {
        var state = interest(player);
        return switch (effect) {
            case VAPOR -> state.vapor;
            case RF -> state.rf;
            case LIGHT -> state.light;
            default -> throw new IllegalArgumentException("Unknown shader effect");
        };
    }

    private static Interest interest(ServerPlayer player) {
        return INTEREST.getOrDefault(player.connection.connection, Interest.EMPTY);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) INTEREST.remove(player.connection.connection);
    }

    public static boolean supportsLocalSources(ServerPlayer player) {
        var connection = player.connection.connection;
        return connection.isMemoryConnection() || INTEREST.containsKey(connection) || hasNegotiatedVersion(connection);
    }

    private static boolean hasNegotiatedVersion(Connection connection) {
        var peer = NetworkHooks.getConnectionData(connection);
        return peer != null && VERSION.equals(peer.getChannels().get(NAME));
    }

    @SubscribeEvent
    public static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var connection = player.connection.connection;
            var current = INTEREST.get(connection);
            if (current != null) {
                INTEREST.put(connection, new Interest(current.effects, current.vapor + 1,
                        current.rf + 1, current.light + 1));
            }
        }
    }
}

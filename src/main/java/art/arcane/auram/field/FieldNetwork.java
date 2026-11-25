package art.arcane.auram.field;

import art.arcane.auram.Auram;
import art.arcane.auram.vapor.VaporState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Consumer;

/** Optional field replication channel. The presence of this channel identifies local source support. */
public final class FieldNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(Auram.MODID, "fields"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(FieldNetwork::accepts)
            .serverAcceptedVersions(FieldNetwork::accepts)
            .simpleChannel();

    private static volatile Consumer<VaporState> vaporReceiver = state -> {};

    private FieldNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(VaporState.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VaporState::encode)
                .decoder(VaporState::decode)
                .consumerMainThread((state, context) -> vaporReceiver.accept(state))
                .add();
    }

    public static void setVaporReceiver(Consumer<VaporState> receiver) {
        vaporReceiver = receiver;
    }

    public static void sendVapor(ServerPlayer player, VaporState state) {
        var connection = player.connection.connection;
        if (connection.isMemoryConnection()) vaporReceiver.accept(state);
        else CHANNEL.sendTo(state, connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static boolean supportsLocalSources(ServerPlayer player) {
        var connection = player.connection.connection;
        return connection.isMemoryConnection() || CHANNEL.isRemotePresent(connection);
    }

    private static boolean accepts(String version) {
        return VERSION.equals(version) || NetworkRegistry.ABSENT.equals(version)
                || NetworkRegistry.ACCEPTVANILLA.equals(version);
    }
}

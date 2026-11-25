package art.arcane.auram.field;

import art.arcane.auram.Auram;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Optional field replication channel. The presence of this channel identifies local source support. */
public final class FieldNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(Auram.MODID, "fields"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(FieldNetwork::accepts)
            .serverAcceptedVersions(FieldNetwork::accepts)
            .simpleChannel();

    private FieldNetwork() {}

    public static void register() {
        // Keep the optional channel in the login handshake even when its sources need no play messages.
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

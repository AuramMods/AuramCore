package art.arcane.auram.map;

import art.arcane.auram.Auram;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class SurfaceMapNetwork {
    static final int MANIFEST_PAGE_SIZE = 128;
    public static final int MAX_REQUESTS = 24;
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(Auram.MODID, "surface_map"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(SurfaceMapNetwork::accepts)
            .serverAcceptedVersions(SurfaceMapNetwork::accepts)
            .simpleChannel();

    public record Hello(UUID world, int nearbyRate, double backgroundRate) {}
    public record View(SurfaceMapView bounds) {}
    public record Discover() {}
    public record Stamp(int x, int z, long revision) {
        public SurfaceTile.Key key(ResourceLocation dimension) {
            return new SurfaceTile.Key(dimension, x, z);
        }
    }
    public record Manifest(ResourceLocation dimension, boolean background, boolean more, List<Stamp> tiles) {}
    public record Fetch(ResourceLocation dimension, List<Stamp> tiles) {}
    public record Tile(ResourceLocation dimension, int x, int z, byte[] encoded) {
        public SurfaceTile.Key key() {
            return new SurfaceTile.Key(dimension, x, z);
        }
    }

    /** Installed at client setup. Common packet handlers load no client classes. */
    public interface ClientReceiver {
        void hello(Hello message);
        void manifest(Manifest message);
        void tile(Tile message);
    }

    private static ClientReceiver receiver;

    private SurfaceMapNetwork() {}

    private static boolean accepts(String remote) {
        return VERSION.equals(remote) || NetworkRegistry.ABSENT.equals(remote) || NetworkRegistry.ACCEPTVANILLA.equals(remote);
    }

    public static void setReceiver(ClientReceiver clientReceiver) {
        receiver = clientReceiver;
    }

    public static void register() {
        CHANNEL.messageBuilder(Hello.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, out) -> {
                    out.writeUUID(message.world);
                    out.writeVarInt(message.nearbyRate);
                    out.writeDouble(message.backgroundRate);
                })
                .decoder(in -> new Hello(in.readUUID(), in.readVarInt(), in.readDouble()))
                .consumerMainThread((message, ctx) -> {
                    if (receiver != null) receiver.hello(message);
                }).add();
        CHANNEL.messageBuilder(View.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, out) -> writeView(out, message.bounds))
                .decoder(in -> new View(readView(in)))
                .consumerMainThread((message, ctx) -> withPlayer(ctx, player -> SurfaceMaps.view(player, message.bounds)))
                .add();
        CHANNEL.messageBuilder(Discover.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, out) -> {})
                .decoder(in -> new Discover())
                .consumerMainThread((message, ctx) -> withPlayer(ctx, SurfaceMaps::discover))
                .add();
        CHANNEL.messageBuilder(Manifest.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, out) -> {
                    out.writeResourceLocation(message.dimension);
                    out.writeBoolean(message.background);
                    out.writeBoolean(message.more);
                    writeStamps(out, message.tiles);
                })
                .decoder(in -> new Manifest(in.readResourceLocation(), in.readBoolean(), in.readBoolean(),
                        readStamps(in, MANIFEST_PAGE_SIZE)))
                .consumerMainThread((message, ctx) -> {
                    if (receiver != null) receiver.manifest(message);
                }).add();
        CHANNEL.messageBuilder(Fetch.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, out) -> {
                    out.writeResourceLocation(message.dimension);
                    writeStamps(out, message.tiles);
                })
                .decoder(in -> new Fetch(in.readResourceLocation(), readStamps(in, MAX_REQUESTS)))
                .consumerMainThread((message, ctx) -> withPlayer(ctx, player -> SurfaceMaps.fetch(player, message)))
                .add();
        CHANNEL.messageBuilder(Tile.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, out) -> {
                    out.writeResourceLocation(message.dimension);
                    out.writeVarInt(message.x);
                    out.writeVarInt(message.z);
                    out.writeByteArray(message.encoded);
                })
                .decoder(in -> new Tile(in.readResourceLocation(), in.readVarInt(), in.readVarInt(),
                        in.readByteArray(SurfaceTile.MAX_ENCODED_BYTES)))
                .consumerMainThread((message, ctx) -> {
                    if (receiver != null) receiver.tile(message);
                }).add();
    }

    private static void withPlayer(Supplier<NetworkEvent.Context> ctx, java.util.function.Consumer<ServerPlayer> handler) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            handler.accept(player);
        }
    }

    private static void writeView(FriendlyByteBuf out, SurfaceMapView view) {
        out.writeResourceLocation(view.dimension());
        out.writeVarInt(view.centerX());
        out.writeVarInt(view.centerZ());
        out.writeVarInt(view.minX());
        out.writeVarInt(view.minZ());
        out.writeVarInt(view.maxX());
        out.writeVarInt(view.maxZ());
    }

    private static SurfaceMapView readView(FriendlyByteBuf in) {
        return new SurfaceMapView(in.readResourceLocation(), in.readVarInt(), in.readVarInt(),
                in.readVarInt(), in.readVarInt(), in.readVarInt(), in.readVarInt());
    }

    private static void writeStamps(FriendlyByteBuf out, List<Stamp> stamps) {
        out.writeVarInt(stamps.size());
        for (Stamp stamp : stamps) {
            out.writeVarInt(stamp.x);
            out.writeVarInt(stamp.z);
            out.writeVarLong(stamp.revision);
        }
    }

    private static List<Stamp> readStamps(FriendlyByteBuf in, int max) {
        int size = in.readVarInt();
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("Invalid surface tile request size");
        }
        List<Stamp> stamps = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stamps.add(new Stamp(in.readVarInt(), in.readVarInt(), in.readVarLong()));
        }
        return stamps;
    }

    static boolean available(ServerPlayer player) {
        return CHANNEL.isRemotePresent(player.connection.connection);
    }

    static void send(ServerPlayer player, Object message) {
        CHANNEL.sendTo(message, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendView(SurfaceMapView view) {
        CHANNEL.sendToServer(new View(view));
    }

    public static void discover() {
        CHANNEL.sendToServer(new Discover());
    }

    public static void fetch(Fetch request) {
        CHANNEL.sendToServer(request);
    }
}

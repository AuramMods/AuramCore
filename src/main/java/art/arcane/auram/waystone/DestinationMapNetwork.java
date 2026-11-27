package art.arcane.auram.waystone;

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

public final class DestinationMapNetwork {
    static final int PAGE_SIZE = 128;
    static final int MAX_NAME_LENGTH = 1024;
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(Auram.MODID, "destination_map"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
            .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
            .simpleChannel();
    private static Consumer<Page> receiver;

    private DestinationMapNetwork() {}

    public record Destination(ResourceLocation dimension, BlockPos pos, String name) {}
    public record Marker(Destination destination, boolean home, boolean warp) {}
    public record Page(boolean first, boolean last, List<Marker> markers) {}

    public static void register() {
        CHANNEL.messageBuilder(Page.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((page, out) -> {
                    out.writeBoolean(page.first());
                    out.writeBoolean(page.last());
                    out.writeCollection(page.markers(), DestinationMapNetwork::writeMarker);
                })
                .decoder(in -> new Page(in.readBoolean(), in.readBoolean(), in.readCollection(FriendlyByteBuf.limitValue(ArrayList::new, PAGE_SIZE), DestinationMapNetwork::readMarker)))
                .consumerMainThread((page, context) -> {
                    if (receiver != null) receiver.accept(page);
                }).add();
    }

    private static void writeMarker(FriendlyByteBuf out, Marker marker) {
        Destination dest = marker.destination();
        out.writeResourceLocation(dest.dimension());
        out.writeBlockPos(dest.pos());
        out.writeUtf(dest.name(), MAX_NAME_LENGTH);
        out.writeBoolean(marker.home());
        out.writeBoolean(marker.warp());
    }

    private static Marker readMarker(FriendlyByteBuf in) {
        return new Marker(new Destination(in.readResourceLocation(), in.readBlockPos(), in.readUtf(MAX_NAME_LENGTH)),
                in.readBoolean(), in.readBoolean());
    }

    public static void setReceiver(Consumer<Page> clientReceiver) {
        receiver = clientReceiver;
    }

    static boolean available(ServerPlayer player) {
        return CHANNEL.isRemotePresent(player.connection.connection);
    }

    static void send(ServerPlayer receiver, List<Marker> markers) {
        if (markers.isEmpty()) {
            CHANNEL.sendTo(new Page(true, true, List.of()), receiver.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        }
        for (int start = 0; start < markers.size(); start += PAGE_SIZE) {
            int end = Math.min(start + PAGE_SIZE, markers.size());
            CHANNEL.sendTo(new Page(start == 0, end == markers.size(), markers.subList(start, end)),
                    receiver.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        }
    }
}

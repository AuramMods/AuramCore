package art.arcane.auram.waystone;

import art.arcane.auram.Auram;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.client.event.MapIconEvent;
import dev.ftb.mods.ftbchunks.api.client.icon.MapIcon;
import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT)
public final class DestinationMapClient {
    private static Map<ResourceLocation, List<DestinationIcon>> icons = Map.of();
    private static final List<DestinationMapNetwork.Marker> incoming = new ArrayList<>();

    private DestinationMapClient() {}

    @Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void register(FMLClientSetupEvent event) {
            if (ModList.get().isLoaded("ftbchunks")) event.enqueueWork(() ->
                    DestinationMapNetwork.setReceiver(DestinationMapClient::receive));
        }
    }

    private static void receive(DestinationMapNetwork.Page page) {
        if (page.first()) incoming.clear();
        incoming.addAll(page.markers());
        if (!page.last()) return;

        Map<ResourceLocation, List<DestinationIcon>> updated = new HashMap<>();
        for (var marker : incoming) {
            updated.computeIfAbsent(marker.destination().dimension(), ignored -> new ArrayList<>()).add(new DestinationIcon(marker));
        }
        icons = updated;
        incoming.clear();
        FTBChunksAPI.clientApi().requestMinimapIconRefresh();
        LargeMapScreen.refreshIconsIfOpen();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        incoming.clear();
        icons = Map.of();
    }

    public static void addIcons(MapIconEvent event) {
        icons.getOrDefault(event.getDimension().location(), List.of()).forEach(event::add);
    }

    public static boolean hasDestinationAt(MapIconEvent event, BlockPos pos) {
        return icons.getOrDefault(event.getDimension().location(), List.of()).stream()
                .anyMatch(icon -> icon.marker.destination().pos().equals(pos));
    }

    private static final class DestinationIcon extends MapIcon.SimpleMapIcon {
        private static final Icon HOME = Icon.getIcon("auram:textures/gui/map_home.png");
        private static final Icon WARP = Icon.getIcon("auram:textures/gui/map_warp.png");
        private final DestinationMapNetwork.Marker marker;

        private DestinationIcon(DestinationMapNetwork.Marker marker) {
            super(Vec3.atCenterOf(marker.destination().pos()), marker.warp() ? WARP : HOME);
            this.marker = marker;
        }

        @Override
        public void addTooltip(TooltipList tooltip) {
            String kind = marker.warp() ? (marker.home() ? "home_and_warp" : "warp") : "home";
            tooltip.add(Component.literal(marker.destination().name()));
            tooltip.add(Component.translatable("auram.waystone.marker." + kind).withStyle(ChatFormatting.GRAY));
            super.addTooltip(tooltip);
        }
    }
}

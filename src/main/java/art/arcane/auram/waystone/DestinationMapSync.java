package art.arcane.auram.waystone;

import art.arcane.auram.Auram;
import dev.ftb.mods.ftbessentials.config.FTBEConfig;
import dev.ftb.mods.ftbessentials.util.FTBEPlayerData;
import dev.ftb.mods.ftbessentials.util.FTBEWorldData;
import dev.ftb.mods.ftbessentials.util.SavedTeleportManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DestinationMapSync {
    private static final int HOME = 1;
    private static final int WARP = 2;
    private static boolean dirty;

    private DestinationMapSync() {}

    public static void changed() {
        dirty = true;
    }

    @Mod.EventBusSubscriber(modid = Auram.MODID)
    public static final class Events {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void playerJoined(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && ModList.get().isLoaded("ftbessentials")) send(player);
        }

        @SubscribeEvent
        public static void publishChanges(TickEvent.ServerTickEvent event) {
            if (!dirty || event.phase != TickEvent.Phase.END || !ModList.get().isLoaded("ftbessentials")) return;
            dirty = false;
            event.getServer().getPlayerList().getPlayers().forEach(DestinationMapSync::send);
        }

        @SubscribeEvent
        public static void clearServer(ServerStoppedEvent event) {
            if (ModList.get().isLoaded("waystones") && ModList.get().isLoaded("ftbessentials")) WaystoneDestinations.changed();
            dirty = false;
        }
    }

    private static void send(ServerPlayer player) {
        if (FTBEWorldData.instance == null || !DestinationMapNetwork.available(player)) return;
        DestinationMapNetwork.send(player, markersFor(player));
    }

    static List<DestinationMapNetwork.Marker> markersFor(ServerPlayer player) {
        Map<DestinationMapNetwork.Destination, Integer> kinds = new LinkedHashMap<>();
        var source = player.createCommandSourceStack();
        if (FTBEConfig.HOME.test(source)) {
            FTBEPlayerData.getOrCreate(player).ifPresent(data -> add(kinds, data.homeManager(), HOME));
        }
        if (FTBEConfig.WARP.test(source)) add(kinds, FTBEWorldData.instance.warpManager(), WARP);

        var markers = new ArrayList<DestinationMapNetwork.Marker>(kinds.size());
        kinds.forEach((destination, kind) -> markers.add(new DestinationMapNetwork.Marker(
                destination, (kind & HOME) != 0, (kind & WARP) != 0)));
        return markers;
    }

    private static void add(Map<DestinationMapNetwork.Destination, Integer> kinds, SavedTeleportManager manager, int kind) {
        manager.destinations().forEach(entry -> {
            var pos = entry.destination();
            // Waystones and player commands are shorter. Bound imported names for the map packet.
            String name = entry.name();
            if (name.length() > DestinationMapNetwork.MAX_NAME_LENGTH) {
                name = name.substring(0, DestinationMapNetwork.MAX_NAME_LENGTH);
            }
            var destination = new DestinationMapNetwork.Destination(pos.getDimension().location(), pos.getPos(), name);
            kinds.merge(destination, kind, (a, b) -> a | b);
        });
    }
}

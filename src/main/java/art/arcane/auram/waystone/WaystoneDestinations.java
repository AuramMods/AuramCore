package art.arcane.auram.waystone;

import art.arcane.auram.mixin.ftbessentials.HomeManagerAccess;
import art.arcane.auram.mixin.ftbessentials.SavedTeleportManagerAccess;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbessentials.util.FTBEPlayerData;
import dev.ftb.mods.ftbessentials.util.FTBEWorldData;
import dev.ftb.mods.ftbessentials.util.SavedTeleportManager;
import dev.ftb.mods.ftbessentials.util.TeleportPos;
import net.blay09.mods.waystones.api.IWaystone;
import net.blay09.mods.waystones.api.WaystoneOrigin;
import net.blay09.mods.waystones.core.WaystoneManager;
import net.blay09.mods.waystones.core.WaystoneTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WaystoneDestinations {
    private static Index index;

    private WaystoneDestinations() {}

    public static boolean isPlayerWaystone(IWaystone stone) {
        return stone.isValid() && stone.getOrigin() == WaystoneOrigin.PLAYER
                && stone.getWaystoneType().equals(WaystoneTypes.WAYSTONE);
    }

    public static boolean isOwnedBy(IWaystone stone, Player player) {
        return isPlayerWaystone(stone) && player.getUUID().equals(stone.getOwnerUid());
    }

    // Match the key format used by the installed FTB Essentials managers.
    public static String key(String name) {
        return name.strip().toLowerCase();
    }

    @Nullable
    public static Component editError(MinecraftServer server, IWaystone stone, String name, boolean global) {
        String key = key(name);
        if (key.isEmpty()) return Component.translatable("auram.waystone.empty_name");

        UUID owner = stone.getOwnerUid();
        var homes = ownerHomes(server, owner);
        WaystoneManager manager = WaystoneManager.get(server);
        if ((homes != null && manual(homes).containsKey(key)) || manager.getWaystones().anyMatch(other ->
                isPlayerWaystone(other) && owner.equals(other.getOwnerUid()) && conflicts(stone, other, key))) {
            return Component.translatable("auram.waystone.home_taken", name);
        }
        if (global && (manual(FTBEWorldData.instance.warpManager()).containsKey(key)
                || manager.getGlobalWaystones().stream().anyMatch(other -> conflicts(stone, other, key)))) {
            return Component.translatable("auram.waystone.warp_taken", name);
        }
        return null;
    }

    @Nullable
    private static SavedTeleportManager.HomeManager ownerHomes(MinecraftServer server, UUID owner) {
        boolean cached = FTBEPlayerData.playerExists(owner);
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        GameProfile profile = online != null ? online.getGameProfile()
                : server.getProfileCache().get(owner).orElseGet(() -> new GameProfile(owner, ""));
        return FTBEPlayerData.getOrCreate(profile).map(data -> {
            // Essentials normally loads this file on login. An administrator may edit an offline owner's stone.
            if (!cached) data.load();
            return data.homeManager();
        }).orElse(null);
    }

    private static boolean conflicts(IWaystone stone, IWaystone other, String key) {
        return !stone.getWaystoneUid().equals(other.getWaystoneUid()) && key.equals(key(other.getName()));
    }

    public static Map<String, TeleportPos> manual(SavedTeleportManager manager) {
        return ((SavedTeleportManagerAccess) manager).auram$manualDestinations();
    }

    public static void changed() {
        index = null;
        DestinationMapSync.changed();
    }

    public static Map<String, IWaystone> forManager(SavedTeleportManager manager) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || server.overworld() == null) return Map.of();
        if (index == null || index.server != server) index = new Index(server);
        if (manager instanceof SavedTeleportManager.WarpManager) return index.warps;
        if (manager instanceof SavedTeleportManager.HomeManager home) {
            UUID owner = ((HomeManagerAccess) home).auram$playerData().getUuid();
            return index.homes.getOrDefault(owner, Map.of());
        }
        return Map.of();
    }

    public static boolean isLinked(SavedTeleportManager manager, String name) {
        String key = key(name);
        return !manual(manager).containsKey(key) && forManager(manager).containsKey(key);
    }

    public static boolean rejectHomeCommand(ServerPlayer player, String name) {
        return FTBEPlayerData.getOrCreate(player).map(data -> rejectCommand(player, data.homeManager(), name))
                .orElse(false);
    }

    public static boolean rejectWarpCommand(ServerPlayer player, String name) {
        return rejectCommand(player, FTBEWorldData.instance.warpManager(), name);
    }

    private static boolean rejectCommand(ServerPlayer player, SavedTeleportManager manager, String name) {
        if (!isLinked(manager, name)) return false;
        player.sendSystemMessage(Component.translatable("auram.waystone.linked_destination", name));
        return true;
    }

    private static final class Index {
        private final MinecraftServer server;
        private final Map<UUID, Map<String, IWaystone>> homes = new HashMap<>();
        private final Map<String, IWaystone> warps = new HashMap<>();

        private Index(MinecraftServer server) {
            this.server = server;
            WaystoneManager.get(server).getWaystones().filter(WaystoneDestinations::isPlayerWaystone).forEach(stone -> {
                String key = key(stone.getName());
                UUID owner = stone.getOwnerUid();
                if (key.isEmpty() || owner == null) return;
                homes.computeIfAbsent(owner, ignored -> new HashMap<>()).merge(key, stone, Index::first);
                if (stone.isGlobal()) warps.merge(key, stone, Index::first);
            });
        }

        // Imported worlds can already have duplicate Waystone names.
        private static IWaystone first(IWaystone a, IWaystone b) {
            return a.getWaystoneUid().compareTo(b.getWaystoneUid()) <= 0 ? a : b;
        }
    }
}

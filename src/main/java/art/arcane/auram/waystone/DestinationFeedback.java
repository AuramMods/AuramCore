package art.arcane.auram.waystone;

import dev.ftb.mods.ftbessentials.util.TeleportPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class DestinationFeedback {
    private DestinationFeedback() {}

    public static MutableComponent named(String key, String name, boolean warp) {
        ChatFormatting accent = warp ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA;
        return Component.translatable(key, Component.literal(name).withStyle(accent)).withStyle(ChatFormatting.GRAY);
    }

    public static void saved(ServerPlayer player, String name, boolean warp) {
        success(player, named(warp ? "auram.waystone.warp_saved" : "auram.waystone.home_saved", name, warp));
    }

    public static void warning(ServerPlayer player, Component message) {
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.YELLOW));
    }

    public static TeleportPos withTravelFeedback(TeleportPos destination, String name, boolean warp) {
        return new TeleportPos(destination.getDimension(), destination.getPos(), destination.yRot, destination.xRot) {
            @Override
            public TeleportResult teleport(ServerPlayer player) {
                TeleportResult result = destination.teleport(player);
                if (result.isSuccess()) success(player, named("auram.waystone.arrived", name, warp));
                return result;
            }
        };
    }

    private static void success(ServerPlayer player, Component message) {
        player.sendSystemMessage(message);
        player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.2F, 1.35F);
    }
}

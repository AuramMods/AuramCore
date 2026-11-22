package art.arcane.auram.smoke;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = "auram_patch_smoke")
public final class ForgeRemoteServerSmoke {
    private ForgeRemoteServerSmoke() {}

    @SubscribeEvent
    public static void joined(PlayerEvent.PlayerLoggedInEvent event) throws Exception {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String mode = System.getProperty("auram.smoke");
        if (!"forge-remote-server".equals(mode) && !("world-startup".equals(mode)
                && Integer.getInteger("auram.smoke.lanPort", 0) != 0 && !player.getName().getString().equals("AuramQA"))) return;
        Files.writeString(Path.of("auram-forge-remote-server-pass.txt"), "Logged in " + player.getName().getString() + "\n");
    }
}

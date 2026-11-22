package art.arcane.auram.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class ForgeRemoteClientSmoke {
    private static boolean done;
    private static boolean connecting;
    private static int titleTicks;

    private ForgeRemoteClientSmoke() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (done || event.phase != TickEvent.Phase.END
                || !"forge-remote".equals(System.getProperty("auram.smoke"))) return;
        Minecraft client = Minecraft.getInstance();
        if (!connecting && client.getOverlay() == null && client.screen instanceof TitleScreen title && ++titleTicks >= 20) {
            connecting = true;
            String server = System.getProperty("auram.smoke.connect");
            ConnectScreen.startConnecting(title, client, ServerAddress.parseString(server),
                    new ServerData("Auram QA server", server, false), false);
        }
        if (client.level == null || client.player == null || client.getSingleplayerServer() != null) return;
        done = true;
        Files.writeString(Path.of("auram-forge-remote-pass.txt"), "Joined the remote server and synchronized the player.\n");
        client.stop();
    }
}

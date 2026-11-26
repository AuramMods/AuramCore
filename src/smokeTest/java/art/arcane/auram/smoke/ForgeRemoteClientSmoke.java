package art.arcane.auram.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
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
    private static int captured;

    private ForgeRemoteClientSmoke() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (done || event.phase != TickEvent.Phase.END
                || !"forge-remote".equals(System.getProperty("auram.smoke"))) return;
        Minecraft client = Minecraft.getInstance();
        if (!connecting && client.getOverlay() == null && client.screen instanceof TitleScreen title && ++titleTicks >= 20) {
            connecting = true;
            WorldLoadClientCapture.begin();
            String server = System.getProperty("auram.smoke.connect");
            ConnectScreen.startConnecting(title, client, ServerAddress.parseString(server),
                    new ServerData("Auram QA server", server, false), false);
        }
        if (WorldLoadCapture.WINDOW_NANOS > 0 || client.level == null || client.player == null
                || client.getSingleplayerServer() != null) return;
        done = true;
        Files.writeString(Path.of("auram-forge-remote-pass.txt"), "Joined the remote server and synchronized the player.\n");
        client.stop();
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) throws Exception {
        if (WorldLoadCapture.WINDOW_NANOS <= 0 || done || !connecting || event.phase != TickEvent.Phase.END
                || !"forge-remote".equals(System.getProperty("auram.smoke"))) return;
        var client = Minecraft.getInstance();
        if (!WorldLoadClientCapture.awaitPlayableWindow(client)) return;
        if (++captured == 2) {
            done = true;
            Files.writeString(Path.of("auram-forge-remote-pass.txt"), "Captured a first join and reconnect.\n");
            client.stop();
        } else {
            done = true;
            client.tell(() -> {
                client.level.disconnect();
                client.clearLevel(new TitleScreen());
                connecting = false;
                titleTicks = 0;
                done = false;
            });
        }
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (WorldLoadCapture.WINDOW_NANOS > 0 && connecting && !done && event.getPlayer() != null) {
            WorldLoadCapture.mark("remote_logged_out_during_capture");
        }
    }
}

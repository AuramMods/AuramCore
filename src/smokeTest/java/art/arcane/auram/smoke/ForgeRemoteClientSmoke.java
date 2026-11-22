package art.arcane.auram.smoke;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class ForgeRemoteClientSmoke {
    private static boolean done;

    private ForgeRemoteClientSmoke() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (done || event.phase != TickEvent.Phase.END
                || !"forge-remote".equals(System.getProperty("auram.smoke"))) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.getSingleplayerServer() != null) return;
        done = true;
        Files.writeString(Path.of("auram-forge-remote-pass.txt"), "Joined the remote server and synchronized the player.\n");
        client.stop();
    }
}

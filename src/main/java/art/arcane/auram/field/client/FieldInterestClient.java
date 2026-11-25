package art.arcane.auram.field.client;

import art.arcane.auram.Auram;
import art.arcane.auram.field.FieldNetwork;
import art.arcane.auram.light.LightClient;
import art.arcane.auram.rf.client.RfFieldClient;
import art.arcane.auram.vapor.client.VaporClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT)
public final class FieldInterestClient {
    private static int options;
    private static int sent = -1;
    private static Connection previous;

    private FieldInterestClient() {}

    public static void configure(boolean vapor, boolean rf, boolean light) {
        int next = (vapor ? FieldNetwork.VAPOR : 0) | (rf ? FieldNetwork.RF : 0) | (light ? FieldNetwork.LIGHT : 0);
        int disabled = options & ~next;
        options = next;
        if ((disabled & FieldNetwork.VAPOR) != 0) VaporClient.reset();
        if ((disabled & FieldNetwork.RF) != 0) RfFieldClient.reset();
        if ((disabled & FieldNetwork.LIGHT) != 0) LightClient.clearRemote();
    }

    public static boolean enabled(int effect) {
        return (options & effect) != 0;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var minecraft = Minecraft.getInstance();
        var listener = minecraft.getConnection();
        Connection connection = listener == null ? null : listener.getConnection();
        if (connection != previous) {
            previous = connection;
            sent = -1;
        }
        if (connection == null || minecraft.player == null || sent == options) return;
        sent = options;
        if (connection.isMemoryConnection()) {
            var integrated = minecraft.getSingleplayerServer();
            if (integrated == null) return;
            var id = minecraft.player.getUUID();
            int requested = options;
            integrated.execute(() -> {
                var player = integrated.getPlayerList().getPlayer(id);
                if (player != null) FieldNetwork.setInterest(player, requested);
            });
        } else {
            FieldNetwork.subscribe(connection, options);
        }
    }
}

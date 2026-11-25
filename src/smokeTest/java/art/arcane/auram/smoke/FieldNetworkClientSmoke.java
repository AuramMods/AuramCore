package art.arcane.auram.smoke;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class FieldNetworkClientSmoke {
    private static final FieldNetworkSmoke.Sample CLIENT = new FieldNetworkSmoke.Sample("client");

    private FieldNetworkClientSmoke() {}

    @SubscribeEvent
    public static void phase(ClientChatReceivedEvent.System event) {
        if (!FieldNetworkSmoke.ENABLED) return;
        String message = event.getMessage().getString();
        if (message.startsWith(FieldNetworkSmoke.PHASE)) {
            CLIENT.advance(message.substring(FieldNetworkSmoke.PHASE.length()), Map.of());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tickStart(TickEvent.ClientTickEvent event) {
        if (FieldNetworkSmoke.ENABLED && event.phase == TickEvent.Phase.START) CLIENT.start();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tickEnd(TickEvent.ClientTickEvent event) {
        if (FieldNetworkSmoke.ENABLED && event.phase == TickEvent.Phase.END) CLIENT.end();
    }
}

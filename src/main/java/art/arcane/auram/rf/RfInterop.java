package art.arcane.auram.rf;

import art.arcane.auram.Auram;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

@Mod.EventBusSubscriber(modid = Auram.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RfInterop {
    private RfInterop() {}

    @SubscribeEvent
    public static void connect(InterModProcessEvent event) {
        event.getIMCStream("rf_routes_v1"::equals).forEach(message -> {
            try {
                @SuppressWarnings("unchecked")
                var accept = (Consumer<BiFunction<ServerLevel, long[], IntConsumer>>) message.messageSupplier().get();
                accept.accept(RfFlow::observeRoute);
            } catch (RuntimeException failure) {
                LogUtils.getLogger().warn("Invalid RF route bridge request from {}", message.senderModId(), failure);
            }
        });
    }
}

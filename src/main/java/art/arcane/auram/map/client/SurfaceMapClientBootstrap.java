package art.arcane.auram.map.client;

import art.arcane.auram.Auram;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Auram.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SurfaceMapClientBootstrap {
    private SurfaceMapClientBootstrap() {}

    @SubscribeEvent
    public static void onSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("journeymap")) {
            event.enqueueWork(SurfaceMapClient::register);
        }
    }
}

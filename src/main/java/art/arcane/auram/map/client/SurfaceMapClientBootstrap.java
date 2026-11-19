package art.arcane.auram.map.client;

import art.arcane.auram.Auram;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;

@Mod.EventBusSubscriber(modid = Auram.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SurfaceMapClientBootstrap {
    private SurfaceMapClientBootstrap() {}

    @SubscribeEvent
    public static void onSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            var renderers = new ArrayList<SurfaceMapRenderer>(2);
            if (ModList.get().isLoaded("journeymap")) {
                renderers.add(new JourneyMapSurface());
            }
            if (ModList.get().isLoaded("ftbchunks")) {
                renderers.add(new FtbChunksSurface());
            }
            if (!renderers.isEmpty()) {
                SurfaceMapClient.register(renderers);
            }
        });
    }
}

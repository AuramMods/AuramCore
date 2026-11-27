package art.arcane.auram.client.menu;

import art.arcane.auram.Auram;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;

import java.util.function.BooleanSupplier;

@Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MenuVoxyInterop {
    private MenuVoxyInterop() {}

    @SubscribeEvent
    public static void registerCoverage(InterModEnqueueEvent event) {
        InterModComms.sendTo("voxy", "auram_menu_coverage_v1", () -> (BooleanSupplier) AuramMenu::coversWorld);
    }
}

package art.arcane.auram.client.menu;

import art.arcane.auram.Auram;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT)
public final class MenuShortcuts {
    private MenuShortcuts() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void removeShortcuts(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof OptionsScreen) {
            // Scaling Animations adds a plain button with a hardcoded label at the end of init.
            event.getListenersList().stream()
                    .filter(widget -> widget instanceof Button button
                            && button.getMessage().getContents() instanceof LiteralContents label
                            && label.text().equals("GUI Animations Settings"))
                    .toList().forEach(event::removeListener);
        } else if (event.getScreen() instanceof PauseScreen) {
            event.getListenersList().stream()
                    .filter(widget -> switch (widget.getClass().getName()) {
                        case "de.keksuccino.justzoom.util.gui.ItemButton",
                             "com.simibubi.create.infrastructure.gui.OpenCreateMenuButton" -> true;
                        default -> false;
                    })
                    .toList().forEach(event::removeListener);
        }
    }
}

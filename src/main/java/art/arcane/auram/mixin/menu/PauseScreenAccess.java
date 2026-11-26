package art.arcane.auram.mixin.menu;

import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PauseScreen.class)
public interface PauseScreenAccess {
    @Accessor("showPauseMenu")
    boolean auram$hasPauseMenu();
}

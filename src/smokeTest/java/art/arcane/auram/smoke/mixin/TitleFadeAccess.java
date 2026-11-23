package art.arcane.auram.smoke.mixin;

import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TitleScreen.class)
public interface TitleFadeAccess {
    @Accessor("fadeInStart")
    long auram$getFadeInStart();
}

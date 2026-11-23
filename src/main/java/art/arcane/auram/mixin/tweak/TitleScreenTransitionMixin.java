package art.arcane.auram.mixin.tweak;

import art.arcane.auram.client.TransitionSettings;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TitleScreen.class)
public abstract class TitleScreenTransitionMixin {
    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/screens/TitleScreen;fading:Z"))
    private boolean auram$skipFade(boolean fading) {
        return fading && !TransitionSettings.INSTANCE.skipTitleFade();
    }
}

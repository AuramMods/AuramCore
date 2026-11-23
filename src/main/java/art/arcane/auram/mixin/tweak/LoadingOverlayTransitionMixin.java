package art.arcane.auram.mixin.tweak;

import art.arcane.auram.client.TransitionSettings;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayTransitionMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private long fadeOutStart;

    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;fadeIn:Z"))
    private boolean auram$skipFadeIn(boolean fadeIn) {
        return fadeIn && !TransitionSettings.INSTANCE.skipSplashFade();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void auram$removeFinishedOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                             CallbackInfo callback) {
        if (TransitionSettings.INSTANCE.skipSplashFade() && fadeOutStart != -1L
                && minecraft.getOverlay() == (Object) this) {
            minecraft.setOverlay(null);
        }
    }
}

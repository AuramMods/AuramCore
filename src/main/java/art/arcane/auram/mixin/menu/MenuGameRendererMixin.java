package art.arcane.auram.mixin.menu;

import art.arcane.auram.client.menu.AuramMenu;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class MenuGameRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/toasts/ToastComponent;render(Lnet/minecraft/client/gui/GuiGraphics;)V"))
    private void auram$drawClosingMenu(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci,
                                       @Local GuiGraphics graphics) {
        AuramMenu.drawClosing(graphics);
    }
}

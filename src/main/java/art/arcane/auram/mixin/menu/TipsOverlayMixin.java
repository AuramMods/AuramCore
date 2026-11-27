package art.arcane.auram.mixin.menu;

import art.arcane.auram.client.menu.AuramMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.darkhax.tipsmod.impl.client.TipRenderHandler", remap = false)
abstract class TipsOverlayMixin {
    @Inject(method = "drawTip", at = @At("HEAD"), cancellable = true)
    private static void auram$skipTipsOnMenu(GuiGraphics graphics, Screen screen, CallbackInfo callback) {
        if (AuramMenu.hasTabs(screen)) callback.cancel();
    }
}

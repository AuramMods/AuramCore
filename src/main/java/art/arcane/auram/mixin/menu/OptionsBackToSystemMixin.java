package art.arcane.auram.mixin.menu;

import art.arcane.auram.client.menu.AuramMenu;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(OptionsScreen.class)
abstract class OptionsBackToSystemMixin {
    @Shadow @Final private Screen lastScreen;

    @ModifyArg(method = "onClose", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private Screen auram$returnToSystem(Screen destination) {
        return AuramMenu.isSystem(lastScreen) ? lastScreen : destination;
    }
}

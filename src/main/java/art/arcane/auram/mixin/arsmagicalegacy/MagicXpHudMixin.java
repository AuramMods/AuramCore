package art.arcane.auram.mixin.arsmagicalegacy;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Pseudo
@Mixin(targets = "com.github.minecraftschurlimods.arsmagicalegacy.client.hud.XpHUD", remap = false)
public abstract class MagicXpHudMixin {
    @Inject(method = "draw", at = @At(value = "INVOKE", target =
            "Lcom/github/minecraftschurlimods/arsmagicalegacy/client/hud/XpHUD;renderBar(Lnet/minecraft/client/gui/GuiGraphics;IIIIDDI)V"),
            cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD, remap = false)
    private void auram$hideInactiveMagicXp(ForgeGui gui, GuiGraphics graphics, float partialTick, CallbackInfo ci,
                                          @Coerce Player player, @Coerce Object api, int level,
                                          double xp, double xpForNextLevel) {
        if (!(xp > 0.0 && xp < xpForNextLevel)) {
            ci.cancel();
        }
    }
}

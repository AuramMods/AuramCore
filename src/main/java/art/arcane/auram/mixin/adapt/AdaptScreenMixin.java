package art.arcane.auram.mixin.adapt;

import art.arcane.adapt.client.screen.AdaptScreen;
import art.arcane.adapt.client.screen.SkillsScreen;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.auram.client.menu.AdaptSkillTree;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AdaptScreen.class, remap = false)
abstract class AdaptScreenMixin {
    @Inject(method = "layoutScreen", at = @At("HEAD"), cancellable = true)
    private void auram$placeSkillsToolbar(UiRect bounds, UiDraw draw, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.player != null && client.level != null && (Object) this instanceof SkillsScreen skills) {
            AdaptSkillTree.layoutToolbar(skills, draw);
            ci.cancel();
        }
    }
}

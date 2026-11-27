package art.arcane.auram.mixin.adapt;

import art.arcane.adapt.client.screen.SkillsScreen;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.KitScreen;
import art.arcane.auram.client.menu.AdaptOverviewRendering;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = KitScreen.class, remap = false)
abstract class AdaptOverviewKitMixin {
    @WrapMethod(method = "render")
    private void auram$drawOverviewControls(UiDraw draw, int mouseX, int mouseY, float partialTick, Operation<Void> original) {
        var client = Minecraft.getInstance();
        if (client.player != null && client.level != null && (Object) this instanceof SkillsScreen) {
            AdaptOverviewRendering.drawNativeControls(() -> original.call(draw, mouseX, mouseY, partialTick));
        } else {
            original.call(draw, mouseX, mouseY, partialTick);
        }
    }
}

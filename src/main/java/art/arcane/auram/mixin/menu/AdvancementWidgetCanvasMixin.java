package art.arcane.auram.mixin.menu;

import art.arcane.auram.client.menu.AuramAdvancementsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(AdvancementWidget.class)
abstract class AdvancementWidgetCanvasMixin {
    @Shadow @Final private AdvancementTab tab;

    @ModifyConstant(method = "drawHover", constant = @Constant(intValue = 113))
    private int auram$canvasTooltipBottom(int vanilla) {
        var screen = tab.getScreen();
        return screen instanceof AuramAdvancementsScreen ? screen.height : vanilla;
    }
}

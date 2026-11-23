package art.arcane.auram.mixin.tweak;

import art.arcane.auram.client.TooltipLayout;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RenderTooltipEvent;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(GuiGraphics.class)
public abstract class TooltipLayoutMixin {
    @Shadow public abstract int guiWidth();

    @ModifyVariable(method = "renderTooltipInternal", argsOnly = true,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/event/RenderTooltipEvent$Pre;isCanceled()Z",
                    remap = false, shift = At.Shift.AFTER))
    private List<ClientTooltipComponent> auram$wrap(
            List<ClientTooltipComponent> components, @Local RenderTooltipEvent.Pre event,
            @Local(argsOnly = true) ClientTooltipPositioner positioner) {
        if (!TooltipLayout.enabled() || event.isCanceled()) return components;
        return TooltipLayout.wrap(components, event.getFont(), event.getX(), guiWidth(),
                positioner == DefaultTooltipPositioner.INSTANCE);
    }

    @WrapOperation(method = "renderTooltipInternal", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"))
    private Vector2ic auram$position(ClientTooltipPositioner positioner, int screenWidth, int screenHeight,
                                       int x, int y, int width, int height, Operation<Vector2ic> original,
                                       @Local(argsOnly = true) List<ClientTooltipComponent> components,
                                       @Local RenderTooltipEvent.Pre event) {
        Vector2ic position = original.call(positioner, screenWidth, screenHeight, x, y, width, height);
        if (!TooltipLayout.enabled() || positioner != DefaultTooltipPositioner.INSTANCE) return position;
        boolean left = TooltipLayout.useLeft(components, event.getFont(), x, screenWidth);
        int limit = Math.max(4, screenWidth - width - 4);
        int drawX = Mth.clamp(left ? x - 16 - width : x + 12, 4, limit);
        return new Vector2i(drawX, Mth.clamp(position.y(), 4, Math.max(4, screenHeight - height - 4)));
    }
}

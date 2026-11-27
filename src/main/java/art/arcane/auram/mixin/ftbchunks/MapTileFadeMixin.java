package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.map.client.MapRegionSurfaceAccess;
import art.arcane.auram.map.client.MapTileFadeRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ftb.mods.ftbchunks.client.gui.MapTileWidget;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MapTileWidget.class, remap = false)
abstract class MapTileFadeMixin {
    @Shadow @Final public MapRegion region;

    @WrapOperation(method = "draw", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftblibrary/ui/GuiHelper;drawTexturedRect(Lnet/minecraft/client/gui/GuiGraphics;IIIILdev/ftb/mods/ftblibrary/icon/Color4I;FFFF)V"))
    private void auram$fadeNewTiles(GuiGraphics graphics, int x, int y, int width, int height, Color4I color,
                                    float u0, float v0, float u1, float v1, Operation<Void> original) {
        var fades = ((MapRegionSurfaceAccess) region).auram$tileFades();
        if (!MapTileFadeRenderer.draw(fades, graphics, x, y, width, height)) {
            original.call(graphics, x, y, width, height, color, u0, v0, u1, v1);
        }
    }
}

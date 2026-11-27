package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.client.menu.AuramMenu;
import art.arcane.auram.map.client.RegionMapSurfaceAccess;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LargeMapScreen.class, remap = false)
abstract class LargeMapNavigationMixin {
    @Shadow @Final private RegionMapPanel regionPanel;
    @Shadow private int zoom;
    @Shadow private int minZoom;
    @Shadow int grabbed;
    @Shadow private boolean movedToPlayer;
    @Shadow private Button claimChunksButton;
    @Shadow private Button waypointManagerButton;
    @Shadow private Button infoButton;
    @Shadow private Button infoSortScreen;
    @Shadow private Button clearDeathpointsButton;
    @Shadow MapDimension dimension;

    @Unique private boolean auram$seededView;
    @Unique private int auram$maximumZoom = 1024;

    @Inject(method = "drawDefaultBackground", at = @At("HEAD"))
    private void auram$useMenuView(GuiGraphics graphics, CallbackInfoReturnable<Boolean> result) {
        if (auram$seededView) return;
        auram$seededView = true;
        var view = AuramMenu.mapView();
        if (view == null || !view.dimension().equals(dimension.dimension)) return;
        zoom = view.zoom();
        auram$maximumZoom = Math.max(auram$maximumZoom, zoom);
        regionPanel.resetScroll();
        regionPanel.scrollTo(view.x() / 512.0, view.z() / 512.0);
        movedToPlayer = true;
    }

    @Inject(method = "addZoom", at = @At("HEAD"), cancellable = true)
    private void auram$zoomAroundCursor(double wheel, CallbackInfo ci) {
        ci.cancel();
        if (wheel == 0) return;
        int target = Math.max(minZoom, Math.min(auram$maximumZoom, wheel > 0 ? zoom * 2 : zoom / 2));
        if (target == zoom) return;
        grabbed = 0;
        var client = Minecraft.getInstance();
        var window = client.getWindow();
        double mouseX = client.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double mouseY = client.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        auram$mapSurface().auram$zoomTo(target * 2, mouseX, mouseY, System.nanoTime());
        zoom = target;
    }

    @Inject(method = "getRegionTileSize", at = @At("HEAD"), cancellable = true)
    private void auram$useAnimatedTileSize(CallbackInfoReturnable<Integer> cir) {
        if (regionPanel == null) return;
        int size = auram$mapSurface().auram$zoomLayoutSize();
        if (size > 0) cir.setReturnValue(size);
    }

    @WrapMethod(method = "drawBackground")
    private void auram$animateMapGrid(GuiGraphics graphics, Theme theme, int x, int y, int width, int height,
                                      Operation<Void> original) {
        var surface = auram$mapSurface();
        surface.auram$advanceZoom(System.nanoTime());
        float scale = (float) surface.auram$zoomScale();
        if (scale == 1) {
            original.call(graphics, theme, x, y, width, height);
            return;
        }
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x * (1 - scale), y * (1 - scale), 0);
        pose.scale(scale, scale, 1);
        try {
            original.call(graphics, theme, x, y, width, height);
        } finally {
            pose.popPose();
        }
    }

    @WrapOperation(method = "drawBackground", at = @At(value = "INVOKE", ordinal = 1,
            target = "Ldev/ftb/mods/ftbchunks/client/gui/RegionMapPanel;setScrollX(D)V"))
    private void auram$keepHorizontalZoomAnchor(RegionMapPanel panel, double scroll, Operation<Void> original) {
        if (auram$mapSurface().auram$zoomLayoutSize() == 0) original.call(panel, scroll);
    }

    @WrapOperation(method = "drawBackground", at = @At(value = "INVOKE", ordinal = 1,
            target = "Ldev/ftb/mods/ftbchunks/client/gui/RegionMapPanel;setScrollY(D)V"))
    private void auram$keepVerticalZoomAnchor(RegionMapPanel panel, double scroll, Operation<Void> original) {
        if (auram$mapSurface().auram$zoomLayoutSize() == 0) original.call(panel, scroll);
    }

    @Unique
    private RegionMapSurfaceAccess auram$mapSurface() {
        return (RegionMapSurfaceAccess) regionPanel;
    }

    @Inject(method = "alignWidgets", at = @At("RETURN"))
    private void auram$placeMapToolsBelowHeader(CallbackInfo ci) {
        auram$shift(claimChunksButton);
        auram$shift(waypointManagerButton);
        auram$shift(infoButton);
        auram$shift(infoSortScreen);
        auram$shift(clearDeathpointsButton);
    }

    @Unique
    private void auram$shift(Button button) {
        button.setY(button.getPosY() + AuramMenu.BAR_HEIGHT);
    }
}

package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.map.client.RegionMapSurfaceAccess;
import art.arcane.auram.map.SurfaceMapView;
import art.arcane.auram.map.client.FtbChunksSurface;
import art.arcane.auram.map.client.FullscreenMapZoom;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RegionMapPanel.class, remap = false)
abstract class RegionMapSurfaceMixin extends Panel implements RegionMapSurfaceAccess {
    @Shadow @Final private LargeMapScreen largeMap;
    @Shadow private int regionMinX;
    @Shadow private int regionMinZ;
    @Unique private boolean auram$regionsChanged;
    @Unique private boolean auram$hasDrawn;
    @Unique private FullscreenMapZoom auram$zoom;

    protected RegionMapSurfaceMixin(Panel parent) {
        super(parent);
    }

    @Override
    public int auram$zoomLayoutSize() {
        return auram$zoom == null ? 0 : auram$zoom.layoutSize();
    }

    @Override
    public double auram$zoomScale() {
        return auram$zoom == null ? 1 : auram$zoom.scale();
    }

    @Override
    public void auram$zoomTo(int size, double mouseX, double mouseY, long now) {
        if (auram$zoom == null) auram$zoom = new FullscreenMapZoom(largeMap.getRegionTileSize());
        auram$zoom.retarget(size, mouseX, mouseY, now);
    }

    @Override
    public void auram$advanceZoom(long now) {
        if (auram$zoom == null) return;
        int oldLayout = auram$zoom.layoutSize();
        double x = auram$zoom.cursorX();
        double y = auram$zoom.cursorY();
        double regionX = auram$zoom.regionAt(regionMinX, getScrollX(), x);
        double regionZ = auram$zoom.regionAt(regionMinZ, getScrollY(), y);
        if (!auram$zoom.advance(now)) return;
        if (oldLayout != auram$zoom.layoutSize()) alignWidgets();
        setScrollX(auram$zoom.scrollFor(regionMinX, regionX, x));
        setScrollY(auram$zoom.scrollFor(regionMinZ, regionZ, y));
    }

    @Override
    public SurfaceMapView auram$view() {
        if (!auram$hasDrawn) {
            return null;
        }
        RegionMapPanel panel = (RegionMapPanel) (Object) this;
        int size = largeMap.getRegionTileSize();
        double displayedSize = auram$zoom == null ? size : auram$zoom.size();
        double minX = (double) regionMinX * 512 + panel.getScrollX() * 512 / size;
        double minZ = (double) regionMinZ * 512 + panel.getScrollY() * 512 / size;
        return FtbChunksSurface.fullscreenView(largeMap.currentDimension().location(), minX, minZ,
                panel.width * 512 / displayedSize, panel.height * 512 / displayedSize);
    }

    @WrapMethod(method = "draw")
    private void auram$drawAtFractionalZoom(GuiGraphics graphics, Theme theme, int x, int y, int w, int h,
                                           Operation<Void> original) {
        if (auram$zoom == null) {
            original.call(graphics, theme, x, y, w, h);
            return;
        }
        float scale = (float) auram$zoom.scale();
        var pose = graphics.pose();
        pose.pushPose();
        // FTB truncates its draw offsets to integers. The pose supplies the remainder.
        pose.translate(x * (1 - scale) - auram$fraction(getScrollX()) * scale,
                y * (1 - scale) - auram$fraction(getScrollY()) * scale, 0);
        pose.scale(scale, scale, 1);
        try {
            original.call(graphics, theme, x, y, w, h);
        } finally {
            pose.popPose();
        }
    }

    @Override
    public void updateMouseOver(int x, int y) {
        if (auram$zoom == null) {
            super.updateMouseOver(x, y);
            return;
        }
        double scale = auram$zoom.scale();
        super.updateMouseOver((int) Math.floor(x / scale + auram$fraction(getScrollX())),
                (int) Math.floor(y / scale + auram$fraction(getScrollY())));
    }

    @WrapOperation(method = "draw", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbchunks/client/gui/RegionMapPanel;getScrollX()D"))
    private double auram$blockUnderMouseX(RegionMapPanel panel, Operation<Double> original) {
        return original.call(panel) + largeMap.getMouseX() * (1 / auram$zoomScale() - 1);
    }

    @WrapOperation(method = "draw", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbchunks/client/gui/RegionMapPanel;getScrollY()D"))
    private double auram$blockUnderMouseY(RegionMapPanel panel, Operation<Double> original) {
        return original.call(panel) + largeMap.getMouseY() * (1 / auram$zoomScale() - 1);
    }

    @Unique
    private static double auram$fraction(double scroll) {
        return scroll - (int) scroll;
    }

    @Override
    public void auram$addedRegion() {
        auram$regionsChanged = true;
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void auram$showNewRegions(GuiGraphics graphics, Theme theme, int x, int y, int w, int h, CallbackInfo ci) {
        if (!auram$regionsChanged) {
            return;
        }
        auram$regionsChanged = false;
        RegionMapPanel panel = (RegionMapPanel) (Object) this;
        int size = largeMap.getRegionTileSize();
        double centerX = regionMinX + (panel.getScrollX() + panel.width / 2.0) / size;
        double centerZ = regionMinZ + (panel.getScrollY() + panel.height / 2.0) / size;
        panel.refreshWidgets();
        panel.scrollTo(centerX, centerZ);
    }

    @Inject(method = "draw", at = @At("TAIL"))
    private void auram$recordInitialLayout(GuiGraphics graphics, Theme theme, int x, int y, int w, int h,
                                           CallbackInfo ci) {
        auram$hasDrawn = true;
    }
}

package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.map.SurfaceMapView;
import art.arcane.auram.map.client.FtbChunksSurface;
import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel;
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
abstract class RegionMapSurfaceMixin implements RegionMapSurfaceAccess {
    @Shadow @Final private LargeMapScreen largeMap;
    @Shadow private int regionMinX;
    @Shadow private int regionMinZ;
    @Unique private boolean auram$regionsChanged;

    @Override
    public SurfaceMapView auram$view() {
        RegionMapPanel panel = (RegionMapPanel) (Object) this;
        int size = largeMap.getRegionTileSize();
        double minX = (double) regionMinX * 512 + panel.getScrollX() * 512 / size;
        double minZ = (double) regionMinZ * 512 + panel.getScrollY() * 512 / size;
        return FtbChunksSurface.fullscreenView(largeMap.currentDimension().location(), minX, minZ,
                (double) panel.width * 512 / size, (double) panel.height * 512 / size);
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
}

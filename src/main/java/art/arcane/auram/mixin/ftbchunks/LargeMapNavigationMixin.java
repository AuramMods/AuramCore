package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.client.menu.AuramMenu;
import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftblibrary.ui.Button;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LargeMapScreen.class, remap = false)
abstract class LargeMapNavigationMixin {
    @Shadow @Final private RegionMapPanel regionPanel;
    @Shadow private int zoom;
    @Shadow private boolean movedToPlayer;
    @Shadow private Button claimChunksButton;
    @Shadow private Button waypointManagerButton;
    @Shadow private Button infoButton;
    @Shadow private Button infoSortScreen;
    @Shadow private Button clearDeathpointsButton;
    @Shadow private Button dimensionButton;
    @Shadow private Button settingsButton;
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

    @ModifyArg(method = "addZoom", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/util/Mth;clamp(III)I"), index = 2)
    private int auram$retainSeededZoomLimit(int nativeLimit) {
        return auram$maximumZoom;
    }

    @Inject(method = "alignWidgets", at = @At("RETURN"))
    private void auram$placeMapToolsBesideRail(CallbackInfo ci) {
        auram$shift(claimChunksButton);
        auram$shift(waypointManagerButton);
        auram$shift(infoButton);
        auram$shift(infoSortScreen);
        auram$shift(clearDeathpointsButton);
        auram$shift(dimensionButton);
        auram$shift(settingsButton);
    }

    @Unique
    private void auram$shift(Button button) {
        button.setX(button.getPosX() + AuramMenu.RAIL_WIDTH);
    }
}

package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.map.client.FtbChunksSurface;
import art.arcane.auram.map.client.MapRegionSurfaceAccess;
import art.arcane.auram.map.client.MapTileFades;
import art.arcane.auram.map.SurfaceTile;
import art.arcane.auram.map.client.SurfaceMapClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MapRegion.class, remap = false)
abstract class MapRegionSurfaceMixin implements MapRegionSurfaceAccess {
    @Unique private volatile SurfaceTile[] auram$surfaceTiles;
    @Unique private volatile boolean auram$streamOnly;
    @Unique private final MapTileFades auram$tileFades = new MapTileFades();

    @Override
    public MapTileFades auram$tileFades() {
        return auram$tileFades;
    }

    @WrapOperation(method = "uploadRenderedMapImage", at = @At(value = "INVOKE", remap = true,
            target = "Lcom/mojang/blaze3d/platform/NativeImage;upload(IIIZ)V"))
    private void auram$recordUploadedTiles(NativeImage image, int level, int x, int z, boolean close,
                                           Operation<Void> original) {
        MapRegion region = (MapRegion) (Object) this;
        boolean fullscreen = Minecraft.getInstance().screen instanceof ScreenWrapper screen
                && screen.getGui() instanceof LargeMapScreen map && map.currentDimension().equals(region.dimension.dimension);
        auram$tileFades.uploaded(index -> image.getPixelRGBA((index % 32) * 16 + 8, (index / 32) * 16 + 8) >>> 24 != 0,
                fullscreen, System.nanoTime());
        original.call(image, level, x, z, close);
    }

    @Override
    public void auram$markStreamOnly() {
        auram$streamOnly = true;
    }

    @Override
    public boolean auram$isStreamOnly() {
        return auram$streamOnly;
    }

    @Inject(method = "markDirty", at = @At("HEAD"))
    private void auram$hasNativeData(CallbackInfo ci) {
        auram$streamOnly = false;
    }

    @Override
    public boolean auram$install(int slot, SurfaceTile tile) {
        MapRegion region = (MapRegion) (Object) this;
        synchronized (region.dimension.getManager().lock) {
            var previous = auram$surfaceTiles;
            var next = previous == null ? new SurfaceTile[16] : previous.clone();
            next[slot] = tile;
            auram$surfaceTiles = next;
            region.update(false);
            return previous == null;
        }
    }

    @Override
    public SurfaceTile[] auram$tiles() {
        return auram$surfaceTiles;
    }

    @Inject(method = "setRenderedMapImageRGBA", at = @At("HEAD"), cancellable = true)
    private void auram$preserveSurfaceUntilComposite(int x, int z, int color, CallbackInfo ci) {
        var tiles = auram$surfaceTiles;
        if (tiles != null && SurfaceMapClient.active() && FtbChunksSurface.hasChunk(tiles, x >> 4, z >> 4)) {
            ci.cancel();
        }
    }

    @Inject(method = "releaseMapImage", at = @At("HEAD"))
    private void auram$releaseSurface(CallbackInfo ci) {
        auram$tileFades.releaseImage();
        MapRegion region = (MapRegion) (Object) this;
        synchronized (region.dimension.getManager().lock) {
            var previous = auram$surfaceTiles;
            auram$surfaceTiles = null;
            if (previous != null) {
                for (int slot = 0; slot < previous.length; slot++) {
                    if (previous[slot] != null) {
                        var key = new SurfaceTile.Key(region.dimension.dimension.location(),
                                region.pos.x() * 4 + slot % 4, region.pos.z() * 4 + slot / 4);
                        SurfaceMapClient.ftbImageReleased(key);
                    }
                }
            }
        }
    }
}

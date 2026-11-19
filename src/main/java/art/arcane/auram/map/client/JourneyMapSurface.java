package art.arcane.auram.map.client;

import art.arcane.auram.map.SurfaceMapView;
import art.arcane.auram.map.SurfaceTile;
import art.arcane.auram.mixin.journeymap.FullscreenGridAccess;
import art.arcane.auram.mixin.journeymap.ImageHolderAccess;
import art.arcane.auram.mixin.journeymap.MiniMapGridAccess;
import com.mojang.blaze3d.platform.NativeImage;
import journeymap.client.api.util.UIState;
import journeymap.client.model.MapState;
import journeymap.client.model.MapType;
import journeymap.client.model.RegionCoord;
import journeymap.client.model.RegionImageCache;
import journeymap.client.model.RegionImageSet;
import journeymap.client.ui.fullscreen.Fullscreen;
import journeymap.client.ui.minimap.MiniMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;

import java.io.File;

final class JourneyMapSurface implements SurfaceMapRenderer {
    @Override
    public Kind kind() {
        return Kind.JOURNEYMAP;
    }

    @Override
    public SurfaceMapView view(Minecraft minecraft, LocalPlayer player) {
        var dimension = player.level().dimension();
        boolean fullscreen = minecraft.screen instanceof Fullscreen;
        if (!fullscreen && minecraft.screen != null) {
            return null;
        }
        var grid = fullscreen ? FullscreenGridAccess.auram$grid() : MiniMapGridAccess.auram$grid();
        UIState state = grid.getUIState();
        var box = grid.getBlockBounds();
        if (state == null || !state.active || box == null || !dimension.equals(state.dimension)) {
            int radius = Math.max(16, minecraft.options.getEffectiveRenderDistance() * 16);
            int x = player.getBlockX();
            int z = player.getBlockZ();
            return SurfaceMapRenderer.blocks(dimension.location(), x, z, x - radius, z - radius, x + radius, z + radius);
        }
        // UIState equality excludes movement. Use the live grid to follow panning at the same zoom.
        return SurfaceMapRenderer.blocks(dimension.location(), (int) Math.floor(grid.getCenterBlockX()),
                (int) Math.floor(grid.getCenterBlockZ()),
                (int) Math.floor(box.minX), (int) Math.floor(box.minZ),
                (int) Math.ceil(box.maxX) - 1, (int) Math.ceil(box.maxZ) - 1);
    }

    @Override
    public boolean apply(SurfaceTile.Key key, SurfaceTile tile) {
        var dimension = ResourceKey.create(Registries.DIMENSION, key.dimension());
        MapState map = MiniMap.state();
        File worldDir = map.getWorldDir();
        if (worldDir == null || !dimension.equals(map.getDimension())) {
            return false;
        }
        MapType surface = MapType.day(dimension);
        ChunkPos first = key.chunkAt(0);
        RegionCoord region = RegionCoord.fromChunkPos(worldDir, surface, first.x, first.z);
        RegionImageSet images = RegionImageCache.INSTANCE.getRegionImageSet(region);
        var holder = images.getHolder(surface);
        holder.getTexture();
        var access = (ImageHolderAccess) (Object) holder;
        try (NativeImage pixels = new NativeImage(16, 16, false)) {
            for (long chunks = tile.version().coverage(); chunks != 0; chunks &= chunks - 1) {
                int slot = Long.numberOfTrailingZeros(chunks);
                ChunkPos chunk = key.chunkAt(slot);
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        pixels.setPixelRGBA(x, z, tile.pixel(slot, x, z));
                    }
                }
                access.auram$blit(pixels, region.getXOffset(chunk.x), region.getZOffset(chunk.z));
            }
        }
        access.auram$flushPixels();
        return true;
    }
}

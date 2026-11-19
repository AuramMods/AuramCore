package art.arcane.auram.map.client;

import art.arcane.auram.map.SurfaceMapView;
import art.arcane.auram.map.SurfaceTile;
import art.arcane.auram.mixin.ftbchunks.LargeMapSurfaceAccess;
import art.arcane.auram.mixin.ftbchunks.MapRegionSurfaceAccess;
import art.arcane.auram.mixin.ftbchunks.RegionMapSurfaceAccess;
import com.mojang.blaze3d.platform.NativeImage;
import dev.ftb.mods.ftbchunks.FTBChunksWorldConfig;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import dev.ftb.mods.ftbchunks.client.FTBChunksClientConfig;
import dev.ftb.mods.ftbchunks.client.gui.ChunkScreen;
import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

public final class FtbChunksSurface implements SurfaceMapRenderer {
    private static final int REGION_TILES = 32 / SurfaceTile.CHUNKS;
    private static final Color4I WEAK_TINT = Color4I.rgba(255, 180, 0, 20);
    private static final Color4I WEAK_ACCENT = Color4I.rgba(255, 180, 0, 100);
    private static final Color4I STRONG_TINT = Color4I.RED.withAlpha(60);
    private static final Color4I STRONG_ACCENT = Color4I.RED.withAlpha(140);

    @Override
    public Kind kind() {
        return Kind.FTB_CHUNKS;
    }

    @Override
    public SurfaceMapView view(Minecraft minecraft, LocalPlayer player) {
        var dimension = player.level().dimension().location();
        if (minecraft.screen instanceof ScreenWrapper screen) {
            if (screen.getGui() instanceof LargeMapScreen large && FTBChunksWorldConfig.playerHasMapStage(player)) {
                return ((RegionMapSurfaceAccess) ((LargeMapSurfaceAccess) (Object) large).auram$regionPanel()).auram$view();
            }
            if (screen.getGui() instanceof ChunkScreen) {
                ChunkPos center = player.chunkPosition();
                return SurfaceMapRenderer.blocks(dimension, player.getBlockX(), player.getBlockZ(),
                        (center.x - 7) * 16, (center.z - 7) * 16, (center.x + 8) * 16 - 1, (center.z + 8) * 16 - 1);
            }
        }
        if (minecraft.screen != null || minecraft.options.hideGui || !FTBChunksClientConfig.MINIMAP_ENABLED.get()
                || FTBChunksClientConfig.MINIMAP_VISIBILITY.get() == 0 || !FTBChunksWorldConfig.shouldShowMinimap(player)) {
            return null;
        }
        double radius = 112 / FTBChunksClient.INSTANCE.getZoom();
        return fullscreenView(dimension, player.getX() - radius, player.getZ() - radius, 2 * radius, 2 * radius);
    }

    public static SurfaceMapView fullscreenView(ResourceLocation dimension, double minX, double minZ,
                                                double width, double height) {
        return SurfaceMapRenderer.blocks(dimension, Mth.floor(minX + width / 2), Mth.floor(minZ + height / 2),
                Mth.floor(minX), Mth.floor(minZ), Mth.ceil(minX + width) - 1, Mth.ceil(minZ + height) - 1);
    }

    @Override
    public boolean apply(SurfaceTile.Key key, SurfaceTile tile) {
        MapDimension dimension = MapDimension.getCurrent().orElse(null);
        if (dimension == null || !dimension.dimension.location().equals(key.dimension())) {
            return false;
        }
        var position = XZ.of(Math.floorDiv(key.x(), REGION_TILES), Math.floorDiv(key.z(), REGION_TILES));
        boolean newRegion = !dimension.getRegions().containsKey(position);
        MapRegion region = dimension.getRegion(position);
        int slot = Math.floorMod(key.x(), REGION_TILES) + Math.floorMod(key.z(), REGION_TILES) * REGION_TILES;
        ((MapRegionSurfaceAccess) region).auram$install(slot, tile);
        if (newRegion && Minecraft.getInstance().screen instanceof ScreenWrapper screen
                && screen.getGui() instanceof LargeMapScreen large && large.currentDimension().equals(dimension.dimension)) {
            ((RegionMapSurfaceAccess) ((LargeMapSurfaceAccess) (Object) large).auram$regionPanel()).auram$addedRegion();
        }
        return true;
    }

    public static boolean covered(ResourceLocation dimension, int chunkX, int chunkZ) {
        return SurfaceMapClient.covered(Kind.FTB_CHUNKS, dimension, chunkX, chunkZ);
    }

    public static boolean hasChunk(SurfaceTile[] tiles, int x, int z) {
        var tile = tiles[x / SurfaceTile.CHUNKS + z / SurfaceTile.CHUNKS * REGION_TILES];
        return tile != null && tile.version().contains(SurfaceTile.slot(x, z));
    }

    public static void paint(MapRegion region, SurfaceTile[] tiles, boolean showAllClaims) {
        var ownTeam = FTBTeamsAPI.api().getClientManager().selfTeam().getTeamId();
        boolean ownClaims = showAllClaims || FTBChunksClientConfig.OWN_CLAIMED_CHUNKS_ON_MAP.get();
        boolean otherClaims = showAllClaims || FTBChunksClientConfig.CLAIMED_CHUNKS_ON_MAP.get();
        boolean grid = FTBChunksClientConfig.CHUNK_GRID.get();
        synchronized (region.dimension.getManager().lock) {
            NativeImage image = region.getRenderedMapImage();
            for (int i = 0; i < tiles.length; i++) {
                SurfaceTile tile = tiles[i];
                if (tile == null) {
                    continue;
                }
                for (long chunks = tile.version().coverage(); chunks != 0; chunks &= chunks - 1) {
                    int slot = Long.numberOfTrailingZeros(chunks);
                    int x = i % REGION_TILES * SurfaceTile.CHUNKS + slot % SurfaceTile.CHUNKS;
                    int z = i / REGION_TILES * SurfaceTile.CHUNKS + slot / SurfaceTile.CHUNKS;
                    paintChunk(region, image, tile, slot, x, z, ownTeam, ownClaims, otherClaims, grid);
                }
            }
        }
    }

    private static void paintChunk(MapRegion region, NativeImage image, SurfaceTile tile, int slot, int cx, int cz,
                                   UUID ownTeam, boolean ownClaims, boolean otherClaims, boolean grid) {
        var chunk = region.getMapChunk(XZ.of(cx, cz));
        var team = chunk == null ? null : chunk.getTeam().orElse(null);
        boolean claimed = team != null && chunk.getClaimedDate().isPresent()
                && (ownTeam.equals(team.getTeamId()) ? ownClaims : otherClaims);
        var color = claimed ? team.getProperty(TeamProperties.COLOR).withAlpha(100) : Color4I.empty();
        boolean top = claimed && !chunk.connects(chunk.offsetBlocking(0, -1));
        boolean bottom = claimed && !chunk.connects(chunk.offsetBlocking(0, 1));
        boolean left = claimed && !chunk.connects(chunk.offsetBlocking(-1, 0));
        boolean right = claimed && !chunk.connects(chunk.offsetBlocking(1, 0));
        int loaded = region.dimension.getLoadedView(region, cx, cz);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int pixel = tile.pixel(slot, x, z);
                if ((top && z == 0) || (bottom && z == 15) || (left && x == 0) || (right && x == 15)) {
                    pixel = swapChannels(0xff000000 | color.rgb());
                } else if (claimed || loaded != 0 || (grid && (x == 0 || z == 0))) {
                    var base = Color4I.rgb(swapChannels(pixel));
                    if (grid && (x == 0 || z == 0)) {
                        base = base.withTint(MapRegion.GRID_COLOR);
                    }
                    boolean accent = x % 4 == (z % 2 == 0 ? 0 : 2);
                    var tint = switch (loaded) {
                        case 1 -> accent ? WEAK_ACCENT : WEAK_TINT;
                        case 2 -> accent ? STRONG_ACCENT : STRONG_TINT;
                        default -> color;
                    };
                    pixel = swapChannels(0xff000000 | base.withTint(tint).rgb());
                }
                image.setPixelRGBA(cx * 16 + x, cz * 16 + z, pixel);
            }
        }
    }

    private static int swapChannels(int color) {
        return color & 0xff00ff00 | (color & 0xff) << 16 | (color >>> 16) & 0xff;
    }
}

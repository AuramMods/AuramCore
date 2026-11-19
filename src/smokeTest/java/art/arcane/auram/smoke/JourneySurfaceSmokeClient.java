package art.arcane.auram.smoke;

import art.arcane.auram.map.client.SurfaceMapClient;
import journeymap.client.JourneymapClient;
import journeymap.common.Journeymap;
import journeymap.client.feature.Feature;
import journeymap.client.feature.FeatureManager;
import journeymap.client.model.MapType;
import journeymap.client.model.RegionCoord;
import journeymap.client.model.RegionImageCache;
import journeymap.client.ui.fullscreen.Fullscreen;
import journeymap.client.task.multi.MapPlayerTask;
import journeymap.client.ui.minimap.MiniMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.chunk.ChunkStatus;

import java.util.Locale;

final class JourneySurfaceSmokeClient {
    private JourneySurfaceSmokeClient() {}

    static void command(Minecraft client, String[] args) {
        switch (args[0]) {
            case "open" -> {
                JourneymapClient.getInstance().getCoreProperties().splashViewed.set(Journeymap.JM_VERSION.toString());
                Fullscreen map = new Fullscreen();
                client.setScreen(map);
                boolean opened = client.screen == map;
                if (opened) {
                    map.centerOn(Double.parseDouble(args[1]), Double.parseDouble(args[2]));
                    for (int zoom = 0; zoom < Integer.parseInt(args[3]); zoom++) {
                        map.zoomOut();
                    }
                }
                System.out.println("SURFACE_TEST OPEN " + opened);
            }
            case "pan" -> {
                if (client.screen instanceof Fullscreen map) {
                    map.centerOn(Double.parseDouble(args[1]), Double.parseDouble(args[2]));
                }
            }
            case "remap" -> MapPlayerTask.forceNearbyRemap();
            case "inspect" -> inspect(client, Integer.parseInt(args[1]), Integer.parseInt(args[2]));
            default -> throw new IllegalArgumentException("Unknown JourneyMap surface command " + args[0]);
        }
    }

    private static void inspect(Minecraft client, int chunkX, int chunkZ) {
        var state = MiniMap.state();
        var dimension = client.level.dimension();
        var layer = MapType.day(dimension);
        boolean imported = SurfaceMapClient.covered(dimension.location(), chunkX, chunkZ);
        int pixel = 0;
        if (state.getWorldDir() != null && dimension.equals(state.getDimension())) {
            RegionCoord region = RegionCoord.fromChunkPos(state.getWorldDir(), layer, chunkX, chunkZ);
            var holder = RegionImageCache.INSTANCE.getRegionImageSet(region).getHolder(layer);
            var image = holder.getTexture().getNativeImage();
            if (image != null) {
                pixel = image.getPixelRGBA(region.getXOffset(chunkX), region.getZOffset(chunkZ));
            }
        }
        boolean caves = FeatureManager.getInstance().isAllowed(Feature.MapCaves);
        boolean loaded = client.level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null;
        var fullMapType = client.screen instanceof Fullscreen map ? map.getMapType() : null;
        System.out.printf(Locale.ROOT, "SURFACE_TEST INSPECT %s %d %d imported=%b loaded=%b caves=%b mini=%s full=%s pixel=%08x playerY=%d%n",
                dimension.location(), chunkX, chunkZ, imported, loaded, caves, state.getMapType().name,
                fullMapType == null ? "closed" : fullMapType.name, pixel, client.player.getBlockY());
    }
}

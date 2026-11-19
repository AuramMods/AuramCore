package art.arcane.auram.smoke;

import art.arcane.auram.map.SurfaceMapNetwork;
import art.arcane.auram.map.SurfaceMapView;
import art.arcane.auram.map.SurfaceTile;
import art.arcane.auram.map.client.SurfaceMapClient;
import journeymap.client.feature.Feature;
import journeymap.client.feature.FeatureManager;
import journeymap.client.model.MapType;
import journeymap.client.model.RegionCoord;
import journeymap.client.model.RegionImageCache;
import journeymap.client.ui.fullscreen.Fullscreen;
import journeymap.client.ui.minimap.MiniMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class SurfaceMapSmokeClient {
    public static final boolean ENABLED = "surfacemap-client".equals(System.getProperty("auram.smoke"));
    private static final Path COMMAND = Path.of("surface-map-command.txt");
    private static final Path READY = Path.of("surface-map-connect-ready");
    private static SurfaceMapView view;
    private static int ticks;
    private static boolean connecting;
    private static String lastCommand = "";

    private SurfaceMapSmokeClient() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) throws IOException {
        if (!ENABLED || event.phase != TickEvent.Phase.END || ++ticks % 5 != 0) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.getOverlay() != null) {
            return;
        }
        if (!connecting && client.screen instanceof TitleScreen title && Files.isRegularFile(READY)) {
            connecting = true;
            String endpoint = Files.readString(READY).trim();
            ConnectScreen.startConnecting(title, client, ServerAddress.parseString(endpoint),
                    new ServerData("Auram surface-map smoke", endpoint, false), false);
            return;
        }
        if (client.screen instanceof DisconnectedScreen) {
            System.out.println("SURFACE_TEST DISCONNECTED " + client.screen.getNarrationMessage().getString());
        }
        if (!Files.isRegularFile(COMMAND)) {
            return;
        }
        String line = Files.readString(COMMAND).trim();
        if (line.isEmpty() || line.equals(lastCommand)) {
            return;
        }
        String[] args = line.split(" +");
        if (args[0].equals("exit")) {
            lastCommand = line;
            client.stop();
            return;
        }
        if (client.level == null || !SurfaceMapClient.active()) {
            return;
        }
        lastCommand = line;
        switch (args[0]) {
            case "open" -> {
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
            case "close" -> client.setScreen(null);
            case "inspect" -> inspect(client, Integer.parseInt(args[1]), Integer.parseInt(args[2]));
            default -> throw new IllegalArgumentException("Unknown surface map test command " + args[0]);
        }
    }

    public static void view(SurfaceMapView bounds) {
        if (ENABLED) {
            view = bounds;
            System.out.printf(Locale.ROOT, "SURFACE_TEST VIEW %d %s %d %d %d %d %d %d%n",
                    System.nanoTime(), bounds.dimension(), bounds.centerX(), bounds.centerZ(),
                    bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ());
        }
    }

    public static void tile(SurfaceMapNetwork.Tile message) {
        if (!ENABLED || view == null || message.encoded().length == 0) {
            return;
        }
        try {
            var version = SurfaceTile.readVersion(new ByteArrayInputStream(message.encoded()));
            System.out.printf(Locale.ROOT, "SURFACE_TEST TILE %d %s %d %d %b %d %d%n",
                    System.nanoTime(), message.dimension(), message.x(), message.z(), view.contains(message.key()),
                    version.revision(), Long.bitCount(version.coverage()));
        } catch (IOException ex) {
            throw new AssertionError("Could not read streamed tile header", ex);
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
        boolean loaded = client.level.hasChunk(chunkX, chunkZ);
        var fullMapType = client.screen instanceof Fullscreen map ? map.getMapType() : null;
        System.out.printf(Locale.ROOT, "SURFACE_TEST INSPECT %s %d %d imported=%b loaded=%b caves=%b mini=%s full=%s pixel=%08x%n",
                dimension.location(), chunkX, chunkZ, imported, loaded, caves, state.getMapType().name,
                fullMapType == null ? "closed" : fullMapType.name, pixel);
    }
}

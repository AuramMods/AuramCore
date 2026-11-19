package art.arcane.auram.smoke;

import art.arcane.auram.map.SurfaceMapNetwork;
import art.arcane.auram.map.SurfaceMapView;
import art.arcane.auram.map.SurfaceTile;
import art.arcane.auram.map.client.SurfaceMapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class SurfaceMapSmokeClient {
    public static final boolean ENABLED = "surfacemap-client".equals(System.getProperty("auram.smoke"));
    private static final Path COMMAND = Path.of("surface-map-command.txt");
    private static final Path READY = Path.of("surface-map-connect-ready");
    private static SurfaceMapView view;
    private static int ticks;
    private static boolean connecting;
    private static String lastCommand = "";

    private SurfaceMapSmokeClient() {}

    @Mod.EventBusSubscriber(modid = "auram_patch_smoke", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Bootstrap {
        @SubscribeEvent
        public static void configure(FMLClientSetupEvent event) {
            if ("surfacemap-client".equals(System.getProperty("auram.smoke"))) {
                event.enqueueWork(() -> MinecraftForge.EVENT_BUS.register(SurfaceMapSmokeClient.class));
            }
        }
    }

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
        if (client.level == null) {
            return;
        }
        lastCommand = line;
        switch (args[0]) {
            case "close" -> client.setScreen(null);
            case "open", "pan", "remap", "inspect" -> JourneySurfaceSmokeClient.command(client, args);
            default -> FtbSurfaceSmokeClient.command(client, args);
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

}

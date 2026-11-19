package art.arcane.auram.smoke;

import art.arcane.auram.map.client.FtbChunksSurface;
import art.arcane.auram.map.client.SurfaceMapClient;
import art.arcane.auram.mixin.ftbchunks.LargeMapSurfaceAccess;
import art.arcane.auram.smoke.mixin.FtbSurfaceClaimProbeMixin;
import dev.ftb.mods.ftbchunks.client.FTBChunksClientConfig;
import dev.ftb.mods.ftbchunks.client.gui.ChunkScreen;
import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapMode;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.level.chunk.ChunkStatus;

import java.io.File;
import java.util.Locale;

public final class FtbSurfaceSmokeClient {
    public static boolean shift;

    private FtbSurfaceSmokeClient() {}

    static void command(Minecraft client, String[] args) {
        switch (args[0]) {
            case "ftb-open" -> LargeMapScreen.openMap();
            case "ftb-claim-screen" -> ChunkScreen.openChunkScreen();
            case "ftb-zoom" -> {
                var screen = (LargeMapScreen) ((ScreenWrapper) client.screen).getGui();
                int steps = Integer.parseInt(args[1]);
                for (int i = 0; i < Math.abs(steps); i++) {
                    screen.addZoom(Integer.signum(steps));
                }
            }
            case "ftb-pan" -> {
                var screen = (LargeMapScreen) ((ScreenWrapper) client.screen).getGui();
                var panel = ((LargeMapSurfaceAccess) (Object) screen).auram$regionPanel();
                panel.scrollTo(Double.parseDouble(args[1]) / 512, Double.parseDouble(args[2]) / 512);
            }
            case "ftb-night" -> {
                FTBChunksClientConfig.MAP_MODE.set(MapMode.NIGHT);
                MapDimension.getCurrent().orElseThrow().getManager().updateAllRegions(false);
            }
            case "ftb-hud" -> FTBChunksClientConfig.MINIMAP_ENABLED.set(true);
            case "ftb-shot" -> Screenshot.grab(new File("."), args[1] + ".png",
                    client.getMainRenderTarget(), message -> System.out.println("FTB_TEST SHOT " + message.getString()));
            case "ftb-inspect" -> inspect(client, Integer.parseInt(args[1]), Integer.parseInt(args[2]));
            case "ftb-claim-op" -> {
                var screen = (ChunkScreen) ((ScreenWrapper) client.screen).getGui();
                ((FtbSurfaceClaimProbeMixin) (Object) screen).auram$selection().add(XZ.of(client.player.chunkPosition()));
                shift = args[1].equals("load") || args[1].equals("unload");
                try {
                    screen.mouseReleased(args[1].equals("claim") || args[1].equals("load") ? MouseButton.LEFT : MouseButton.RIGHT);
                } finally {
                    shift = false;
                }
            }
            default -> throw new IllegalArgumentException("Unknown FTB surface command " + args[0]);
        }
    }

    static void inspect(Minecraft client, int x, int z) {
        var dim = MapDimension.getCurrent().orElseThrow();
        boolean covered = FtbChunksSurface.covered(dim.dimension.location(), x, z);
        boolean loaded = client.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) != null;
        var region = dim.getRegions().get(XZ.regionFromChunk(x, z));
        int first = 0;
        int interior = 0;
        boolean claimed = false;
        boolean forced = false;
        if (region != null) {
            synchronized (dim.getManager().lock) {
                var image = region.getRenderedMapImage();
                int bx = (x & 31) * 16;
                int bz = (z & 31) * 16;
                first = image.getPixelRGBA(bx, bz);
                interior = image.getPixelRGBA(bx + 7, bz + 7);
                var chunk = region.getMapChunk(XZ.of(x & 31, z & 31));
                if (chunk != null) {
                    claimed = chunk.getClaimedDate().isPresent();
                    forced = chunk.getForceLoadedDate().isPresent();
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "FTB_TEST INSPECT %s %d %d active=%b covered=%b loaded=%b claim=%b force=%b first=%08x interior=%08x playerY=%d%n",
                dim.dimension.location(), x, z, SurfaceMapClient.active(), covered, loaded, claimed, forced, first, interior,
                client.player.getBlockY());
    }
}

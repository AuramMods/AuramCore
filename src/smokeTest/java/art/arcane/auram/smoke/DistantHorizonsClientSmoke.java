package art.arcane.auram.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fly a fixed route on a real connection, with a stationary interval before and after flight. */
@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class DistantHorizonsClientSmoke {
    private static final int STATIONARY_TICKS = Integer.getInteger("auram.dh.stationaryTicks", 600);
    private static final int START_IDLE_TICKS = Integer.getInteger("auram.dh.startIdleTicks", STATIONARY_TICKS);
    private static final int FLIGHT_BLOCKS = Integer.getInteger("auram.dh.flightBlocks", 800);
    private static final boolean WAIT_FOR_SERVER = Boolean.getBoolean("auram.dh.waitForServer");
    private static final Path SERVER_READY = Path.of("dh-connect-ready");
    private static final List<String> SAMPLES = new ArrayList<>();
    private static boolean connecting, finished;
    private static double startX, startZ;
    private static int ticks, phase, endTicks;
    private static long started;
    public static int visibleBuffers;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) throws IOException {
        if (!DistantHorizonsSmoke.CLIENT || event.phase != TickEvent.Phase.END || finished) return;
        var client = Minecraft.getInstance();
        if (client.getOverlay() != null) return;
        if (!connecting && client.screen instanceof TitleScreen title) {
            if (WAIT_FOR_SERVER && !Files.isRegularFile(SERVER_READY)) return;
            connecting = true;
            String endpoint = System.getProperty("auram.dh.endpoint");
            ConnectScreen.startConnecting(title, client, ServerAddress.parseString(endpoint),
                    new ServerData("Auram DH smoke", endpoint, false), false);
            return;
        }
        if (client.screen instanceof DisconnectedScreen) {
            throw new AssertionError("DH smoke client disconnected: " + client.screen.getNarrationMessage().getString());
        }
        var player = client.player;
        if (player == null || client.level == null || client.screen != null || !player.getAbilities().mayfly) return;
        int flightY = client.level.getMaxBuildHeight() + DistantHorizonsSmoke.FLIGHT_CLEARANCE;
        if (started == 0) {
            if (!player.getAbilities().flying || player.getY() < flightY) return;
            if (GLFW.glfwGetWindowAttrib(client.getWindow().getWindow(), GLFW.GLFW_VISIBLE) != GLFW.GLFW_FALSE) {
                throw new AssertionError("DH fixture must run with a hidden client window");
            }
            started = System.nanoTime();
            startX = player.getX();
            startZ = player.getZ();
        }
        double destinationX = startX + FLIGHT_BLOCKS;
        if (ticks == START_IDLE_TICKS) phase = 1;
        // Follow any server correction instead of resending a stale route coordinate.
        double nextX = phase == 0 ? startX : Math.min(destinationX, player.getX() + 1.25);
        player.setDeltaMovement(0, 0, 0);
        player.setPos(nextX, flightY, startZ);
        if (phase != 0) {
            phase = nextX == destinationX ? 2 : 1;
            endTicks = phase == 2 ? endTicks + 1 : 0;
        }
        if (endTicks == STATIONARY_TICKS) {
            SAMPLES.add(0, "elapsed_ns,client_tick,phase,center_x,center_z,loaded,visible_buffers");
            Files.write(Path.of("dh-client.csv"), SAMPLES);
            Files.writeString(Path.of("dh-client-pass.txt"), "Completed stationary, flight and settle phases\n");
            finished = true;
            client.stop();
            return;
        }
        player.setYRot(-90);
        player.setXRot(0);
        var position = player.chunkPosition();
        var chunks = client.level.getChunkSource();
        int loaded = 0;
        for (int x = position.x - 2; x <= position.x + 2; x++) {
            for (int z = position.z - 2; z <= position.z + 2; z++) {
                if (chunks.getChunk(x, z, ChunkStatus.FULL, false) != null) loaded++;
            }
        }
        SAMPLES.add(DistantHorizonsSmoke.csv(System.nanoTime() - started, ticks, phase,
                position.x, position.z, loaded, visibleBuffers));
        if (ticks % 200 == 0) {
            System.out.printf("DH smoke tick %d phase %d position %s%n", ticks, phase, player.position());
        }
        ticks++;
    }
}

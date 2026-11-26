package art.arcane.auram.smoke;

import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

public final class WorldLoadClientCapture {
    private static final WorldLoadCapture.TickSeries FRAMES = new WorldLoadCapture.TickSeries();
    private static int join;
    private static long joinAt;
    private static long playableAt;
    private static long previousFrame;
    private static boolean jeiStarted;
    private static boolean searchChecked;
    private static InventoryScreen sampleInventory;
    private static boolean earlyInventoryChecked;

    private WorldLoadClientCapture() {}

    public static void begin() {
        if (WorldLoadCapture.WINDOW_NANOS <= 0) return;
        join++;
        joinAt = System.nanoTime();
        playableAt = 0;
        previousFrame = 0;
        jeiStarted = false;
        searchChecked = false;
        sampleInventory = null;
        earlyInventoryChecked = false;
        WorldLoadCapture.mark("client_join_begin_" + join);
    }

    public static void jeiReady() {
        if (joinAt != 0) {
            jeiStarted = true;
            WorldLoadCapture.mark("jei_started_" + join);
        }
    }

    static long playableMillis(long start) {
        return (playableAt - start) / 1_000_000L;
    }

    static boolean awaitPlayableWindow(Minecraft client) throws ReflectiveOperationException {
        if (WorldLoadCapture.WINDOW_NANOS <= 0) return true;
        if (joinAt == 0) throw new IllegalStateException("World-load capture has no active join");
        if (client.level == null || client.player == null) return false;
        long now = System.nanoTime();
        if (playableAt == 0) {
            var chunk = new ChunkPos(client.player.blockPosition());
            if (client.screen != null || client.getOverlay() != null
                    || !client.level.getChunkSource().hasChunk(chunk.x, chunk.z)) return false;
            if (GLFW.glfwGetWindowAttrib(client.getWindow().getWindow(), GLFW.GLFW_VISIBLE) != GLFW.GLFW_FALSE
                    || client.options.getSoundSourceVolume(SoundSource.MASTER) != 0
                    || client.options.narrator().get() != NarratorStatus.OFF) {
                throw new IllegalStateException("World-load client must be hidden, muted, and have the narrator off");
            }
            playableAt = now;
            WorldLoadCapture.mark("first_playable_frame_" + join);
        }
        if (previousFrame != 0) FRAMES.add(now - playableAt, now - previousFrame);
        previousFrame = now;
        if (sampleInventory != null) {
            if (client.screen == sampleInventory) client.setScreen(null);
            sampleInventory = null;
        }
        if (Boolean.getBoolean("auram.smoke.earlyInventory") && !earlyInventoryChecked) {
            earlyInventoryChecked = true;
            WorldLoadCapture.mark("first_inventory_open_begin_" + join);
            sampleInventory = new InventoryScreen(client.player);
            client.setScreen(sampleInventory);
            WorldLoadCapture.mark("first_inventory_open_end_" + join);
        }
        if (!searchChecked && jeiStarted) verifySearch();
        if (now - playableAt < WorldLoadCapture.WINDOW_NANOS) return false;
        if (ModList.get().isLoaded("jei") && !searchChecked) {
            throw new IllegalStateException("JEI search was unavailable throughout the world-load capture");
        }
        FRAMES.save("world-load-client-frames-" + join + ".csv", "frame_ms");
        WorldLoadCapture.mark("client_capture_complete_" + join);
        joinAt = 0;
        return true;
    }

    private static void verifySearch() throws ReflectiveOperationException {
        var runtime = (IJeiRuntime) Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null);
        if (runtime == null) return;
        var filter = runtime.getIngredientFilter();
        var previous = filter.getFilterText();
        try {
            WorldLoadCapture.mark("first_jei_search_begin_" + join);
            filter.setFilterText("furnace");
            int matches = filter.getFilteredItemStacks().size();
            if (matches == 0) throw new IllegalStateException("JEI did not find furnace ingredients");
            searchChecked = true;
            WorldLoadCapture.mark("first_jei_search_end_" + join + "_matches_" + matches);
            if (Boolean.getBoolean("auram.smoke.stoneWorkParity")) StoneWorkRecipeParity.capture(runtime, join);
        } finally {
            filter.setFilterText(previous);
        }
    }
}

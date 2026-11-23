package art.arcane.auram.smoke;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import art.arcane.auram.smoke.mixin.WidgetAlphaAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class TransitionSmoke {
    private TransitionSmoke() {
    }

    static void run(Minecraft client) throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(
                Path.of("config/auram/skip-transitions.json"))).getAsJsonObject();
        GuiGraphics graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
        var title = new TitleScreen(true);
        title.init(client, graphics.guiWidth(), graphics.guiHeight());
        for (var child : title.children()) {
            if (child instanceof AbstractWidget widget) widget.setAlpha(0.0F);
        }
        title.render(graphics, 0, 0, 0);
        boolean visible = title.children().stream()
                .filter(AbstractWidget.class::isInstance)
                .map(widget -> (AbstractWidget) widget)
                .anyMatch(widget -> ((WidgetAlphaAccess) widget).auram$getAlpha() == 1.0F);
        require(visible == setting(config, "removeTitleScreenFade"), "title button fade");
        try {
            verifyReload(client, graphics, setting(config, "removeSplashFade"), false);
            verifyReload(client, graphics, setting(config, "removeSplashFade"), true);
            verifyToast(client, graphics, setting(config, "removeToastsSlide"), setting(config, "removeToastsSFX"));
        } finally {
            client.setOverlay(null);
            graphics.flush();
        }
        Files.writeString(Path.of("auram-transition-smoke.txt"), "Passed title, successful and failed reload, toast position and sound\n");
    }

    private static void verifyReload(Minecraft client, GuiGraphics graphics, boolean skipFade, boolean failed) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        var reload = new ReloadInstance() {
            @Override public CompletableFuture<?> done() { return done; }
            @Override public float getActualProgress() { return done.isDone() ? 1.0F : 0.5F; }
        };
        List<Optional<Throwable>> outcomes = new ArrayList<>();
        var overlay = new LoadingOverlay(client, reload, outcomes::add, false);
        client.setOverlay(overlay);
        overlay.render(graphics, 0, 0, 0);
        require(client.getOverlay() == overlay && outcomes.isEmpty(), "pending resource reload stays visible");
        if (failed) done.completeExceptionally(new IllegalStateException("Simulated reload failure"));
        else done.complete(null);
        overlay.render(graphics, 0, 0, 0);
        require(outcomes.size() == 1 && outcomes.get(0).isPresent() == failed, "reload callback preserves result");
        require((client.getOverlay() == null) == skipFade, "completed resource overlay fade");
    }

    private static void verifyToast(Minecraft client, GuiGraphics graphics, boolean skipSlide, boolean skipSound) {
        var recorder = new ToastSounds();
        MinecraftForge.EVENT_BUS.register(recorder);
        try {
            List<Float> xPositions = new ArrayList<>();
            var toasts = new ToastComponent(client);
            toasts.addToast((canvas, component, elapsed) -> {
                xPositions.add(canvas.pose().last().pose().m30());
                return Toast.Visibility.HIDE;
            });
            toasts.render(graphics);
            toasts.render(graphics);
            require(!xPositions.isEmpty(), "toast rendered");
            require((Math.abs(xPositions.get(0) - (graphics.guiWidth() - 160)) < 1) == skipSlide,
                    "toast placement " + xPositions);
            require((recorder.in == 0 && recorder.out == 0) == skipSound, "toast whoosh");
            if (!skipSound) require(recorder.in == 1 && recorder.out == 1, "toast start and end sounds");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(recorder);
        }
    }

    private static boolean setting(JsonObject config, String key) {
        return config.get("enabled").getAsBoolean() && (!config.has(key) || config.get(key).getAsBoolean());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static final class ToastSounds {
        private int in;
        private int out;

        @SubscribeEvent
        public void onSound(PlaySoundEvent event) {
            var id = event.getOriginalSound().getLocation();
            if (id.equals(SoundEvents.UI_TOAST_IN.getLocation())) in++;
            else if (id.equals(SoundEvents.UI_TOAST_OUT.getLocation())) out++;
        }
    }
}

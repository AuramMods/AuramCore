package art.arcane.auram.client.scroll;

import art.arcane.auram.tweak.TweakConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "auram", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SmoothScrolling {
    private static volatile Settings settings = read();

    private SmoothScrolling() {
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> settings = read());
    }

    private static Settings read() {
        return TweakConfig.read("smooth-scroll.json", Settings.class, new Settings());
    }

    public static Settings settings() {
        return settings;
    }

    public static double advance(double current, double target, double smoothness) {
        if (smoothness <= 0 || Math.abs(current - target) < 0.01) return target;
        double elapsedTicks = Math.max(0, Minecraft.getInstance().getDeltaFrameTime());
        return target + (current - target) * Math.pow(smoothness, elapsedTicks);
    }

    public static void mask(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        graphics.enableScissor(x1, y1, x2, y2);
        if (settings.maskDebug) graphics.fill(x1, y1, x2, y2, 0x3200FFFF);
    }

    public static final class Settings {
        private boolean enabled;
        private double hotbar = 0.05;
        private boolean rollover = true;
        private double chat = 0.5;
        private double chatOpening = 0.5;
        private double creative = 0.5;
        private double entryList = 0.5;
        private double entryListSpeed = 30.0;
        private boolean maskDebug;

        public boolean enabled() { return enabled; }
        public double hotbar() { return enabled ? factor(hotbar) : 0; }
        public boolean rollover() { return rollover; }
        public double chat() { return enabled ? factor(chat) : 0; }
        public double chatOpening() { return enabled ? factor(chatOpening) : 0; }
        public double creative() { return enabled ? factor(creative) : 0; }
        public double entryList() { return enabled ? factor(entryList) : 0; }
        public double entryListSpeed() { return entryListSpeed > 0 && Double.isFinite(entryListSpeed) ? entryListSpeed : 0; }

        private static double factor(double value) {
            return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
        }
    }
}

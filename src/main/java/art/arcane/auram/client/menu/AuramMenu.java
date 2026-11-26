package art.arcane.auram.client.menu;

import art.arcane.auram.Auram;
import art.arcane.auram.mixin.menu.PauseScreenAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.List;

@Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT)
public final class AuramMenu {
    public static final int BAR_HEIGHT = 30;
    private static final long FADE_NANOS = 1_000_000_000L;
    private static final MenuBackdrop backdrop = new MenuBackdrop();
    private static final MenuGlass glass = new MenuGlass();
    private static final Tab[] tabs = Tab.values();
    private static ClientLevel world;
    private static double centerX;
    private static double centerZ;
    private static Tab lastTab;
    private static long fadeStart;
    private static int capturedMouseButton = -1;

    private AuramMenu() {}

    enum Tab {
        SYSTEM("system"), MAP("map"), JOURNAL("journal"), SKILLS("skills");

        private final Component title;

        Tab(String name) {
            title = Component.translatable("gui.auram.menu." + name);
        }
    }

    private static Tab tab(Screen screen) {
        if (screen instanceof PauseScreen && ((PauseScreenAccess) screen).auram$hasPauseMenu()) return Tab.SYSTEM;
        if (screen instanceof OpeningSkills) return Tab.SKILLS;
        if (MenuIntegrations.loaded(Tab.MAP) && FtbMapMenu.matches(screen)) return Tab.MAP;
        if (MenuIntegrations.loaded(Tab.JOURNAL) && MenuIntegrations.isJournal(screen)) return Tab.JOURNAL;
        if (MenuIntegrations.loaded(Tab.SKILLS) && MenuIntegrations.isSkillScreen(screen)) return Tab.SKILLS;
        return null;
    }

    private static boolean active(Screen screen) {
        var client = Minecraft.getInstance();
        return screen == client.screen && client.player != null && client.level != null;
    }

    public static boolean isJournalGui(Object gui) {
        return Minecraft.getInstance().level != null && MenuIntegrations.isJournalGui(gui);
    }

    public static MapMenuView mapView() {
        return backdrop.view();
    }

    private static boolean available(Minecraft client, Tab tab) {
        return MenuIntegrations.loaded(tab) && (tab != Tab.MAP || FtbMapMenu.available(client.player));
    }

    @SubscribeEvent
    public static void prepare(ScreenEvent.Render.Pre event) {
        if (!active(event.getScreen())) return;
        var currentTab = tab(event.getScreen());
        if (currentTab == null) return;
        var client = Minecraft.getInstance();
        long now = System.nanoTime();
        if (world != client.level) {
            reset(client);
            world = client.level;
            centerX = client.player.getX();
            centerZ = client.player.getZ();
        }
        if (lastTab != currentTab) {
            fadeStart = lastTab != null || currentTab == Tab.MAP ? now : 0;
            lastTab = currentTab;
        }
        backdrop.prepare(client, available(client, Tab.MAP), centerX, centerZ,
                event.getScreen().width, event.getScreen().height, now);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void background(ScreenEvent.BackgroundRendered event) {
        if (!active(event.getScreen())) return;
        var currentTab = tab(event.getScreen());
        if (currentTab == Tab.SYSTEM || currentTab == Tab.SKILLS) {
            backdrop.render(event.getGuiGraphics(), event.getScreen().width, event.getScreen().height, 1.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderTabs(ScreenEvent.Render.Post event) {
        var screen = event.getScreen();
        if (!active(screen)) return;
        var selected = tab(screen);
        if (selected == null) return;
        var client = Minecraft.getInstance();
        var graphics = event.getGuiGraphics();
        float progress = Mth.clamp((System.nanoTime() - fadeStart) / (float) FADE_NANOS, 0.0F, 1.0F);
        if (progress < 1.0F) {
            // Exponential ease-out with both endpoints normalized for a one-second fade.
            float cover = (float) ((Math.pow(2, -8 * progress) - 1.0 / 256.0) / (1 - 1.0 / 256.0));
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 1000);
            backdrop.render(graphics, screen.width, screen.height, cover);
            graphics.pose().popPose();
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1100);
        glass.draw(client, graphics, screen.width, screen.height, selected == Tab.JOURNAL ? BAR_HEIGHT : 0, BAR_HEIGHT);
        graphics.hLine(0, screen.width - 1, BAR_HEIGHT - 1, 0xff354956);

        var visible = visibleTabs(client);
        int tabWidth = tabWidth(screen.width, visible.size());
        int left = (screen.width - tabWidth * visible.size()) / 2;
        boolean enabled = !nativeModal(screen, selected);
        for (var entry : visible) {
            int right = left + tabWidth;
            boolean hovered = enabled && event.getMouseY() >= 0 && event.getMouseY() < BAR_HEIGHT
                    && event.getMouseX() >= left && event.getMouseX() < right;
            drawTab(graphics, client, entry, left, right, entry == selected, hovered);
            left = right;
        }
        graphics.pose().popPose();
    }

    private static void drawTab(GuiGraphics graphics, Minecraft client, Tab tab, int left, int right,
                                boolean selected, boolean hovered) {
        int text = 0xd0dce4;
        if (selected || hovered) {
            graphics.fillGradient(left + 2, 1, right - 2, BAR_HEIGHT - 1, 0x05308ca0,
                    selected ? 0x633aa2aa : 0x3a54b3ba);
            graphics.hLine(left + 9, right - 10, BAR_HEIGHT - 2, selected ? 0xff9feee5 : 0x9059bec1);
            graphics.hLine(left + 13, right - 14, BAR_HEIGHT - 3, selected ? 0x55a6ede4 : 0x3059bec1);
            text = selected ? 0xe6fffa : 0xffffff;
        }
        graphics.drawCenteredString(client.font, tab.title, (left + right) / 2,
                (BAR_HEIGHT - client.font.lineHeight) / 2, text);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouseDown(ScreenEvent.MouseButtonPressed.Pre event) {
        var screen = event.getScreen();
        if (!active(screen) || event.getMouseY() < 0 || event.getMouseY() >= BAR_HEIGHT) return;
        var selected = tab(screen);
        if (selected == null || nativeModal(screen, selected)) return;
        event.setCanceled(true);
        capturedMouseButton = event.getButton();
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

        var client = Minecraft.getInstance();
        var visible = visibleTabs(client);
        int width = tabWidth(screen.width, visible.size());
        double offset = event.getMouseX() - (screen.width - width * visible.size()) / 2;
        if (offset < 0 || offset >= width * visible.size()) return;
        openTab(client, screen, selected, visible.get((int) (offset / width)));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouseUp(ScreenEvent.MouseButtonReleased.Pre event) {
        if (capturedMouseButton == event.getButton()) {
            capturedMouseButton = -1;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouseDrag(ScreenEvent.MouseDragged.Pre event) {
        if (capturedMouseButton == event.getMouseButton()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!active(event.getScreen()) || event.getMouseY() < 0 || event.getMouseY() >= BAR_HEIGHT) return;
        var selected = tab(event.getScreen());
        if (selected != null && !nativeModal(event.getScreen(), selected)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void nextTab(ScreenEvent.KeyPressed.Pre event) {
        if (!active(event.getScreen()) || event.getKeyCode() != GLFW.GLFW_KEY_TAB
                || (event.getModifiers() & GLFW.GLFW_MOD_CONTROL) == 0) return;
        var selected = tab(event.getScreen());
        if (selected == null || nativeModal(event.getScreen(), selected)) return;
        var client = Minecraft.getInstance();
        var visible = visibleTabs(client);
        int direction = (event.getModifiers() & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1;
        int index = Math.floorMod(visible.indexOf(selected) + direction, visible.size());
        event.setCanceled(true);
        openTab(client, event.getScreen(), selected, visible.get(index));
    }

    private static boolean nativeModal(Screen screen, Tab tab) {
        return (tab == Tab.MAP || tab == Tab.JOURNAL) && MenuIntegrations.nativeModal(screen);
    }

    private static List<Tab> visibleTabs(Minecraft client) {
        return Arrays.stream(tabs).filter(tab -> available(client, tab)).toList();
    }

    private static int tabWidth(int width, int count) {
        return Mth.clamp((width - 12) / count, 1, 94);
    }

    private static void openTab(Minecraft client, Screen from, Tab selected, Tab target) {
        if (selected == target) return;
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
        switch (target) {
            case SYSTEM -> client.setScreen(new PauseScreen(true));
            case MAP -> FtbMapMenu.open();
            case JOURNAL -> MenuIntegrations.openJournal();
            case SKILLS -> {
                if (MenuIntegrations.requestSkills() && client.screen == from) {
                    // The Adapt server reply has to arrive even when the System tab paused singleplayer.
                    client.setScreen(new OpeningSkills(from));
                }
            }
        }
    }

    @SubscribeEvent
    public static void clearAfterMenu(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || world == null) return;
        var client = Minecraft.getInstance();
        if (client.level != world || client.screen == null) reset(client);
    }

    private static void reset(Minecraft client) {
        backdrop.close(client);
        world = null;
        lastTab = null;
        fadeStart = 0;
        capturedMouseButton = -1;
    }

    private static final class OpeningSkills extends Screen {
        private final Screen previous;
        private final long expires = System.nanoTime() + 4_000_000_000L;

        private OpeningSkills(Screen previous) {
            super(Tab.SKILLS.title);
            this.previous = previous;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void tick() {
            if (System.nanoTime() >= expires) onClose();
        }

        @Override
        public void onClose() {
            minecraft.setScreen(previous);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            graphics.drawCenteredString(font, Component.translatable("gui.auram.menu.opening_skills"),
                    width / 2, height / 2, 0xe6fffa);
        }
    }
}

package art.arcane.auram.client.menu;

import art.arcane.auram.Auram;
import art.arcane.auram.mixin.menu.PauseScreenAccess;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

@Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT)
public final class AuramMenu {
    public static final int RAIL_WIDTH = 36;
    private static final MenuBackdrop backdrop = new MenuBackdrop();
    private static final MenuSidebar sidebar = new MenuSidebar();
    private static final MenuMotion motion = new MenuMotion();
    private static final Tab[] tabs = Tab.values();
    private static ClientLevel world;
    private static double centerX;
    private static double centerZ;
    private static Tab lastTab;
    private static Screen renderedScreen;
    private static long fadeStart;
    private static int capturedMouseButton = -1;

    private AuramMenu() {}

    enum Tab {
        SYSTEM("gui.auram.menu.system", "", "menu_gear"),
        MAP("gui.auram.menu.map", "key.ftbchunks.map", "menu_map"),
        JOURNAL("gui.auram.menu.journal", "key.ftbquests.quests", "menu_journal"),
        SKILLS("gui.auram.menu.skills", "key.adapt.open_skills", "menu_skills"),
        ADVANCEMENTS("gui.advancements", "key.advancements", "menu_trophy");

        final Component title;
        private final String binding;
        final ResourceLocation icon;

        Tab(String titleKey, String binding, String icon) {
            title = Component.translatable(titleKey);
            this.binding = binding;
            this.icon = new ResourceLocation(Auram.MODID, "textures/gui/" + icon + ".png");
        }
    }

    private static Tab tab(Screen screen) {
        if (isSystem(screen)) return Tab.SYSTEM;
        if (screen instanceof AdvancementsScreen) return Tab.ADVANCEMENTS;
        if (screen instanceof SkillsStatus) return Tab.SKILLS;
        if (MenuIntegrations.loaded(Tab.MAP) && FtbMapMenu.matches(screen)) return Tab.MAP;
        if (MenuIntegrations.loaded(Tab.JOURNAL) && MenuIntegrations.isJournal(screen)) return Tab.JOURNAL;
        if (MenuIntegrations.loaded(Tab.SKILLS) && MenuIntegrations.isSkillScreen(screen)) return Tab.SKILLS;
        return null;
    }

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        var client = Minecraft.getInstance();
        if (event.getNewScreen() != null && event.getNewScreen().getClass() == AdvancementsScreen.class
                && client.player != null && client.level != null) {
            event.setNewScreen(new AuramAdvancementsScreen(client.player.connection.getAdvancements()));
        }
    }

    static boolean sidebarContains(double x, double y, int height) {
        return sidebar.contains(x, y, height);
    }

    public static boolean isSystem(Screen screen) {
        return screen instanceof PauseScreen && ((PauseScreenAccess) screen).auram$hasPauseMenu();
    }

    private static boolean active(Screen screen) {
        var client = Minecraft.getInstance();
        return screen == client.screen && client.player != null && client.level != null;
    }

    public static boolean hasTabs(Screen screen) {
        return active(screen) && tab(screen) != null;
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
            motion.startOpen(now);
        }
        if (lastTab != currentTab) {
            fadeStart = lastTab != null ? now : 0;
            lastTab = currentTab;
        }
        backdrop.prepare(client, available(client, Tab.MAP), centerX, centerZ,
                event.getScreen().width, event.getScreen().height, now);
        motion.captureOpening(client, event.getGuiGraphics(), now);
        if (currentTab == Tab.JOURNAL) {
            // FTB Quests opts out of the default screen background.
            backdrop.render(event.getGuiGraphics(), event.getScreen().width, event.getScreen().height, 1);
        }
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
        float cover = MenuMotion.cover(fadeStart, System.nanoTime());
        if (cover > 0) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 1000);
            backdrop.render(graphics, screen.width, screen.height, cover);
            graphics.pose().popPose();
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1100);
        sidebar.draw(client, graphics, screen.width, screen.height, event.getMouseX(), event.getMouseY(),
                selected, sidebarRows(client, screen), !nativeModal(screen, selected));
        graphics.pose().popPose();
        motion.drawOpening(graphics);
        renderedScreen = screen;
    }

    @SubscribeEvent
    public static void closing(ScreenEvent.Closing event) {
        if (event.getScreen() == renderedScreen && active(event.getScreen())) {
            motion.captureClosing(Minecraft.getInstance());
            renderedScreen = null;
        }
    }

    public static void drawClosing(GuiGraphics graphics) {
        motion.drawClosing(Minecraft.getInstance(), graphics);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void mouseDown(ScreenEvent.MouseButtonPressed.Pre event) {
        var screen = event.getScreen();
        if (!active(screen)) return;
        var selected = tab(screen);
        if (selected == null || nativeModal(screen, selected)) return;
        var client = Minecraft.getInstance();
        if (!sidebar.contains(event.getMouseX(), event.getMouseY(), screen.height)) {
            if (event.isCanceled()) return;
            var target = boundTab(client, key -> key.matchesMouse(event.getButton()) && key.getKeyModifier().isActive(null));
            if (target != null) {
                event.setCanceled(true);
                capturedMouseButton = event.getButton();
                openTab(client, selected, target);
            }
            return;
        }
        event.setCanceled(true);
        capturedMouseButton = event.getButton();
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

        var rows = sidebarRows(client, screen);
        int row = sidebar.rowAt(event.getMouseY(), screen.height, rows.size());
        if (row < 0) return;
        if (row < rows.tabs().size()) {
            openTab(client, selected, rows.tabs().get(row));
        } else if (screen instanceof AuramAdvancementsScreen advancements) {
            advancements.selectCategory(rows.categories().get(row - rows.tabs().size()));
        }
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
        var screen = event.getScreen();
        if (!active(screen) || !sidebar.contains(event.getMouseX(), event.getMouseY(), screen.height)) return;
        var selected = tab(event.getScreen());
        if (selected != null && !nativeModal(screen, selected)) {
            sidebar.scroll(event.getScrollDelta(), screen.height, sidebarRows(Minecraft.getInstance(), screen).size());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void menuKeys(ScreenEvent.KeyPressed.Pre event) {
        if (!active(event.getScreen())) return;
        var selected = tab(event.getScreen());
        if (selected == null) return;
        var client = Minecraft.getInstance();
        if (event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE) {
            // Adapt handles prompts and parent pages before closing the Skills root.
            if (selected == Tab.SKILLS && !(event.getScreen() instanceof SkillsStatus)) return;
            event.setCanceled(true);
            client.setScreen(null);
            return;
        }
        if (nativeModal(event.getScreen(), selected)) return;
        var target = boundTab(client, key -> key.matches(event.getKeyCode(), event.getScanCode())
                && matchesModifier(key, event.getModifiers()));
        if (target != null) {
            event.setCanceled(true);
            openTab(client, selected, target);
            return;
        }
        if (event.getKeyCode() != GLFW.GLFW_KEY_TAB || (menuModifiers(event.getModifiers()) & GLFW.GLFW_MOD_CONTROL) == 0) return;
        var visible = visibleTabs(client);
        int direction = (event.getModifiers() & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1;
        int index = Math.floorMod(visible.indexOf(selected) + direction, visible.size());
        event.setCanceled(true);
        openTab(client, selected, visible.get(index));
    }

    private static Tab boundTab(Minecraft client, Predicate<KeyMapping> pressed) {
        for (var mapping : client.options.keyMappings) {
            for (var tab : tabs) {
                if (mapping.getName().equals(tab.binding) && available(client, tab) && pressed.test(mapping)) return tab;
            }
        }
        return null;
    }

    private static boolean matchesModifier(KeyMapping binding, int modifiers) {
        int mask = switch (binding.getKeyModifier()) {
            case SHIFT -> GLFW.GLFW_MOD_SHIFT;
            case CONTROL -> GLFW.GLFW_MOD_CONTROL;
            case ALT -> GLFW.GLFW_MOD_ALT;
            case NONE -> 0;
        };
        return mask == menuModifiers(modifiers);
    }

    private static int menuModifiers(int modifiers) {
        if (Minecraft.ON_OSX && (modifiers & GLFW.GLFW_MOD_SUPER) != 0) modifiers |= GLFW.GLFW_MOD_CONTROL;
        return modifiers & (GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT);
    }

    private static boolean nativeModal(Screen screen, Tab tab) {
        return (tab == Tab.MAP || tab == Tab.JOURNAL) && MenuIntegrations.nativeModal(screen);
    }

    private static MenuSidebar.Rows sidebarRows(Minecraft client, Screen screen) {
        var tabs = visibleTabs(client);
        return screen instanceof AuramAdvancementsScreen advancements
                ? new MenuSidebar.Rows(tabs, advancements.categories(), advancements.selectedCategory())
                : new MenuSidebar.Rows(tabs, List.of(), null);
    }

    private static List<Tab> visibleTabs(Minecraft client) {
        return Arrays.stream(tabs).filter(tab -> available(client, tab)).toList();
    }

    private static void openTab(Minecraft client, Tab selected, Tab target) {
        if (selected == target) return;
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
        switch (target) {
            case SYSTEM -> client.setScreen(new PauseScreen(true));
            case MAP -> FtbMapMenu.open();
            case JOURNAL -> MenuIntegrations.openJournal();
            case SKILLS -> {
                var result = MenuIntegrations.openSkills();
                if (result != MenuIntegrations.SkillsResult.OPEN) client.setScreen(new SkillsStatus(result));
            }
            case ADVANCEMENTS -> client.setScreen(new AuramAdvancementsScreen(client.player.connection.getAdvancements()));
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
        sidebar.reset();
        world = null;
        lastTab = null;
        renderedScreen = null;
        fadeStart = 0;
        capturedMouseButton = -1;
    }

    private static final class SkillsStatus extends Screen {
        private final long expires = System.nanoTime() + 4_000_000_000L;
        private long nextCheck;
        private MenuIntegrations.SkillsResult result;

        private SkillsStatus(MenuIntegrations.SkillsResult result) {
            super(Tab.SKILLS.title);
            this.result = result;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void tick() {
            long now = System.nanoTime();
            if (result != MenuIntegrations.SkillsResult.WAITING || now < nextCheck) return;
            nextCheck = now + 500_000_000L;
            result = MenuIntegrations.openSkills();
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            String message = switch (result) {
                case OPEN, WAITING -> System.nanoTime() < expires ? "opening_skills" : "skills_unavailable";
                case DISABLED -> "skills_disabled";
                case UNAVAILABLE -> "skills_unavailable";
            };
            graphics.drawCenteredString(font, Component.translatable("gui.auram.menu." + message),
                    width / 2, height / 2, 0xe6fffa);
        }
    }
}

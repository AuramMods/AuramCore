package art.arcane.auram.client.menu;

import art.arcane.auram.Auram;
import art.arcane.auram.mixin.menu.PauseScreenAccess;
import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

@Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT)
public final class AuramMenu {
    public static final int BAR_HEIGHT = 30;
    private static final int ICON_SIZE = 16;
    private static final int ICON_TAB_WIDTH = 36;
    private static final int TAB_GAP = 4;
    private static final MenuBackdrop backdrop = new MenuBackdrop();
    private static final MenuGlass glass = new MenuGlass();
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
        MAP("gui.auram.menu.map", "key.ftbchunks.map", null),
        JOURNAL("gui.auram.menu.journal", "key.ftbquests.quests", null),
        SKILLS("gui.auram.menu.skills", "key.adapt.open_skills", null),
        ADVANCEMENTS("gui.advancements", "key.advancements", "menu_trophy");

        private final Component title;
        private final String binding;
        private final ResourceLocation icon;

        Tab(String titleKey, String binding, String icon) {
            title = Component.translatable(titleKey);
            this.binding = binding;
            this.icon = icon == null ? null : new ResourceLocation(Auram.MODID, "textures/gui/" + icon + ".png");
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
        if (currentTab == Tab.SKILLS && MenuIntegrations.isSkillScreen(event.getScreen())) {
            var client = Minecraft.getInstance();
            var window = client.getWindow();
            int x = (int) (client.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
            int y = (int) (client.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
            AdaptSkillTree.render(event.getScreen(), event.getGuiGraphics(), x, y);
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
        glass.drawTop(client, graphics, screen.width, screen.height, BAR_HEIGHT);
        graphics.hLine(0, screen.width - 1, BAR_HEIGHT - 1, 0xff354956);

        boolean enabled = !nativeModal(screen, selected);
        Component tooltip = null;
        for (var slot : tabSlots(client, screen.width)) {
            boolean hovered = enabled && slot.contains(event.getMouseX(), event.getMouseY());
            drawTab(graphics, client, slot, slot.tab() == selected, hovered);
            if (hovered && slot.tab().icon != null) tooltip = slot.tab().title;
        }
        if (screen instanceof AuramAdvancementsScreen advancements) {
            advancements.renderCategoryRail(graphics, event.getMouseX(), event.getMouseY());
        }
        if (tooltip != null) {
            graphics.renderTooltip(client.font, tooltip, event.getMouseX(), BAR_HEIGHT + 12);
        }
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

    private static void drawTab(GuiGraphics graphics, Minecraft client, TabSlot slot,
                                boolean selected, boolean hovered) {
        int left = slot.left();
        int right = slot.right();
        int text = 0xd0dce4;
        if (selected || hovered) {
            graphics.fillGradient(left + 2, 1, right - 2, BAR_HEIGHT - 1, 0x05308ca0,
                    selected ? 0x633aa2aa : 0x3a54b3ba);
            graphics.hLine(left + 9, right - 10, BAR_HEIGHT - 2, selected ? 0xff9feee5 : 0x9059bec1);
            graphics.hLine(left + 13, right - 14, BAR_HEIGHT - 3, selected ? 0x55a6ede4 : 0x3059bec1);
            text = selected ? 0xe6fffa : 0xffffff;
        }
        int center = (left + right) / 2;
        if (slot.tab().icon == null) {
            graphics.drawCenteredString(client.font, slot.tab().title, center,
                    (BAR_HEIGHT - client.font.lineHeight) / 2, text);
        } else {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.blit(slot.tab().icon, center - ICON_SIZE / 2, (BAR_HEIGHT - ICON_SIZE) / 2,
                    0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            RenderSystem.disableBlend();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void mouseDown(ScreenEvent.MouseButtonPressed.Pre event) {
        var screen = event.getScreen();
        if (!active(screen)) return;
        var selected = tab(screen);
        if (selected == null || nativeModal(screen, selected)) return;
        var client = Minecraft.getInstance();
        if (event.getMouseY() < 0 || event.getMouseY() >= BAR_HEIGHT) {
            if (event.isCanceled()) return;
            var target = boundTab(client, key -> key.matchesMouse(event.getButton()) && key.getKeyModifier().isActive(null));
            if (target != null) {
                event.setCanceled(true);
                capturedMouseButton = event.getButton();
                openTab(client, selected, target);
            } else if (selected == Tab.SKILLS && AdaptSkillTree.press(screen, event.getMouseX(), event.getMouseY(), event.getButton())) {
                event.setCanceled(true);
            }
            return;
        }
        if (screen instanceof AuramAdvancementsScreen advancements) advancements.dismissCategoryRail();
        event.setCanceled(true);
        capturedMouseButton = event.getButton();
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

        for (var slot : tabSlots(client, screen.width)) {
            if (slot.contains(event.getMouseX(), event.getMouseY())) {
                openTab(client, selected, slot.tab());
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouseUp(ScreenEvent.MouseButtonReleased.Pre event) {
        if (capturedMouseButton == event.getButton()) {
            capturedMouseButton = -1;
            event.setCanceled(true);
        } else if (active(event.getScreen()) && tab(event.getScreen()) == Tab.SKILLS
                && AdaptSkillTree.release(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouseDrag(ScreenEvent.MouseDragged.Pre event) {
        if (capturedMouseButton == event.getMouseButton()
                || active(event.getScreen()) && tab(event.getScreen()) == Tab.SKILLS
                && AdaptSkillTree.drag(event.getScreen(), event.getMouseButton(), event.getDragX(), event.getDragY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        var screen = event.getScreen();
        if (!active(screen)) return;
        var selected = tab(screen);
        if (selected == null || nativeModal(screen, selected)) return;
        if (event.getMouseY() >= 0 && event.getMouseY() < BAR_HEIGHT
                || selected == Tab.SKILLS && AdaptSkillTree.scroll(screen, event.getMouseX(), event.getMouseY(), event.getScrollDelta())) {
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
        if (nativeModal(event.getScreen(), selected) || selected == Tab.SKILLS && AdaptSkillTree.editingText(event.getScreen())) return;
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

    private static List<Tab> visibleTabs(Minecraft client) {
        return Arrays.stream(tabs).filter(tab -> available(client, tab)).toList();
    }

    private static List<TabSlot> tabSlots(Minecraft client, int width) {
        var visible = visibleTabs(client);
        var slots = new ArrayList<TabSlot>(visible.size());
        int labelStart = ICON_TAB_WIDTH + TAB_GAP;
        int labelWidth = Mth.clamp((width - 2 * labelStart) / Math.max(1, visible.size() - 2), 1, 94);
        slots.add(new TabSlot(Tab.SYSTEM, 0, ICON_TAB_WIDTH));
        int left = labelStart;
        for (var tab : visible) {
            if (tab.icon != null) continue;
            slots.add(new TabSlot(tab, left, left + labelWidth));
            left += labelWidth;
        }
        slots.add(new TabSlot(Tab.ADVANCEMENTS, width - ICON_TAB_WIDTH, width));
        return slots;
    }

    private record TabSlot(Tab tab, int left, int right) {
        boolean contains(double x, double y) {
            return x >= left && x < right && y >= 0 && y < BAR_HEIGHT;
        }
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
        if (MenuIntegrations.loaded(Tab.SKILLS)) AdaptSkillTree.reset();
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

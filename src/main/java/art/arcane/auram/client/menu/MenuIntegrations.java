package art.arcane.auram.client.menu;

import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

final class MenuIntegrations {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String QUEST = "dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen";
    private static final String ADAPT = "art.arcane.adapt.";
    private static final String ADAPT_WRAPPER = ADAPT + "client.ui.MinecraftUiScreen";
    private static final String ADAPT_SCREEN = ADAPT + "client.screen.AdaptScreen";

    private static Method openJournal;
    private static AdaptSkills adaptSkills;
    private static Method skillContent;
    private static boolean failedJournal;
    private static boolean failedSkills;

    private MenuIntegrations() {}

    static boolean loaded(AuramMenu.Tab tab) {
        return switch (tab) {
            case SYSTEM, ADVANCEMENTS -> true;
            case MAP -> ModList.get().isLoaded("ftbchunks");
            case JOURNAL -> ModList.get().isLoaded("ftbquests") && !failedJournal;
            case SKILLS -> ModList.get().isLoaded("adapt");
        };
    }

    static boolean isJournalGui(Object gui) {
        return gui.getClass().getName().equals(QUEST);
    }

    static boolean isJournal(Screen screen) {
        Object gui = FtbScreens.base(screen);
        return gui != null && isJournalGui(gui);
    }

    static boolean nativeModal(Screen screen) {
        return FtbScreens.hasModal(screen);
    }

    static void openJournal() {
        try {
            if (openJournal == null) {
                openJournal = Class.forName("dev.ftb.mods.ftbquests.client.ClientQuestFile").getMethod("openGui");
            }
            openJournal.invoke(null);
        } catch (ReflectiveOperationException | LinkageError error) {
            failedJournal = true;
            LOGGER.warn("Auram could not open the FTB Quests screen", error);
        }
    }

    static boolean isSkillScreen(Screen screen) {
        if (failedSkills || !screen.getClass().getName().equals(ADAPT_WRAPPER)) return false;
        try {
            if (skillContent == null) {
                skillContent = screen.getClass().getDeclaredMethod("content");
                skillContent.setAccessible(true);
            }
            Object content = skillContent.invoke(screen);
            for (Class<?> type = content.getClass(); type != null; type = type.getSuperclass()) {
                if (type.getName().equals(ADAPT_SCREEN)) return true;
            }
        } catch (ReflectiveOperationException | LinkageError error) {
            failedSkills = true;
            LOGGER.warn("Auram could not identify Adapt's skills screen", error);
        }
        return false;
    }

    enum SkillsResult { OPEN, WAITING, DISABLED, UNAVAILABLE }

    static SkillsResult openSkills() {
        if (failedSkills) return SkillsResult.UNAVAILABLE;
        try {
            if (adaptSkills == null) adaptSkills = AdaptSkills.link();
            Object client = adaptSkills.instance.invoke(null);
            if (client == null) return SkillsResult.WAITING;
            Object net = adaptSkills.net.invoke(client);
            if (!(boolean) adaptSkills.nativeUi.invoke(net)) return SkillsResult.DISABLED;
            Object state = adaptSkills.state.invoke(net);
            if (!(boolean) adaptSkills.hasCapability.invoke(state, 1)) return SkillsResult.WAITING;

            Object sink = adaptSkills.routes.invoke(adaptSkills.sinks.invoke(net));
            // Open the root of the already-negotiated screens locally. Adapt still owns
            // the displayed player data and sends actual skill actions to its server.
            if (!adaptSkills.router.isInstance(sink)) {
                return SkillsResult.UNAVAILABLE;
            }
            adaptSkills.open.invoke(sink, adaptSkills.skills.newInstance());
            return SkillsResult.OPEN;
        } catch (ReflectiveOperationException | LinkageError error) {
            failedSkills = true;
            LOGGER.warn("Auram could not open the Adapt Skills screen", error);
            return SkillsResult.UNAVAILABLE;
        }
    }

    private record AdaptSkills(Method instance, Method net, Method nativeUi, Method state, Method hasCapability,
                               Method sinks, Method routes, Class<?> router, Method open, Constructor<?> skills) {
        static AdaptSkills link() throws ReflectiveOperationException {
            Class<?> client = Class.forName(ADAPT + "client.AdaptClient");
            Method net = client.getMethod("net");
            Class<?> netType = net.getReturnType();
            Method sinks = netType.getMethod("sinks");
            Method state = netType.getMethod("state");
            Class<?> router = Class.forName(ADAPT + "client.screen.ScreenRouter");
            Class<?> route = Class.forName(ADAPT + "client.protocol.Route");
            return new AdaptSkills(client.getMethod("instance"), net, netType.getMethod("nativeUi"), state,
                    state.getReturnType().getMethod("has", int.class), sinks, sinks.getReturnType().getMethod("routes"),
                    router, router.getMethod("open", route), Class.forName(route.getName() + "$Skills").getConstructor());
        }
    }

    // Linking the FTB Library types here keeps the vanilla and Adapt-only menus independent of FTB.
    private static final class FtbScreens {
        private static Object base(Screen screen) {
            return screen instanceof ScreenWrapper wrapper ? wrapper.getGui() : null;
        }

        private static boolean hasModal(Screen screen) {
            return base(screen) instanceof BaseScreen gui
                    && (gui.anyModalPanelOpen() || gui.getContextMenu().isPresent());
        }
    }
}

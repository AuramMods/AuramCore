package art.arcane.auram.client.menu;

import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;

final class MenuIntegrations {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String QUEST = "dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen";
    private static final String ADAPT = "art.arcane.adapt.";
    private static final String ADAPT_WRAPPER = ADAPT + "client.ui.MinecraftUiScreen";
    private static final String ADAPT_SCREEN = ADAPT + "client.screen.AdaptScreen";

    private static Method openJournal;
    private static Method adaptClient;
    private static Method adaptNet;
    private static Method openSkills;
    private static Method skillContent;
    private static boolean failedJournal;
    private static boolean failedSkills;

    private MenuIntegrations() {}

    static boolean loaded(AuramMenu.Tab tab) {
        return switch (tab) {
            case SYSTEM -> true;
            case MAP -> ModList.get().isLoaded("ftbchunks");
            case JOURNAL -> ModList.get().isLoaded("ftbquests") && !failedJournal;
            case SKILLS -> ModList.get().isLoaded("adapt") && !failedSkills;
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
        if (!screen.getClass().getName().equals(ADAPT_WRAPPER)) return false;
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

    static boolean requestSkills() {
        try {
            if (openSkills == null) {
                var client = Class.forName(ADAPT + "client.AdaptClient");
                adaptClient = client.getMethod("instance");
                adaptNet = client.getMethod("net");
                var screens = Class.forName(ADAPT + "client.screen.ClientScreens");
                openSkills = screens.getDeclaredMethod("openSkills", adaptNet.getReturnType());
                openSkills.setAccessible(true);
            }
            Object client = adaptClient.invoke(null);
            return client != null && (boolean) openSkills.invoke(null, adaptNet.invoke(client));
        } catch (ReflectiveOperationException | LinkageError error) {
            failedSkills = true;
            LOGGER.warn("Auram could not request the Adapt Skills screen", error);
            return false;
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

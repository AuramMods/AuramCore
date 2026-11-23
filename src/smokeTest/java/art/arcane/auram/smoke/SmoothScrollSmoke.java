package art.arcane.auram.smoke;

import art.arcane.auram.smoke.mixin.ClientTimerAccess;
import com.google.gson.Gson;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class SmoothScrollSmoke {
    private SmoothScrollSmoke() {
    }

    static void run(Minecraft client) throws Exception {
        var timer = ((ClientTimerAccess) client).auram$getTimer();
        float previousDelta = timer.tickDelta;
        double originalScale = client.getWindow().getGuiScale();
        try {
            timer.tickDelta = 0.25F;
            Map<String, Object> sample = renderWidgets(client);
            timer.tickDelta = 0.1F;
            client.getWindow().setGuiScale(originalScale > 1 ? originalScale - 1 : originalScale + 1);
            double[] second = selectionList(client, new GuiGraphics(client, client.renderBuffers().bufferSource()));
            Files.writeString(Path.of("auram-smooth-scroll-smoke.json"), new Gson().toJson(
                    Map.of("native_scale", originalScale, "native_scale_widgets", sample,
                            "other_scale", client.getWindow().getGuiScale(), "other_scale_list_pixels", second)) + "\n");
        } finally {
            timer.tickDelta = previousDelta;
            client.getWindow().setGuiScale(originalScale);
        }
    }

    private static Map<String, Object> renderWidgets(Minecraft client) throws Exception {
        GuiGraphics graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
        double[] listPositions = selectionList(client, graphics);
        var hotbar = client.player.getInventory();
        int oldSlot = hotbar.selected;
        try {
            hotbar.selected = 0;
            client.gui.renderHotbar(0, graphics);
            hotbar.swapPaint(1);
            require(hotbar.selected == 8, "hotbar wheel selection did not wrap");
            client.gui.renderHotbar(0, graphics);
            hotbar.swapPaint(-1);
            require(hotbar.selected == 0, "reverse hotbar wheel selection did not wrap");
            client.gui.renderHotbar(0, graphics);
        } finally {
            hotbar.selected = oldSlot;
        }
        var chat = client.gui.getChat();
        for (int i = 0; i < 24; i++) chat.addMessage(Component.literal("Auram history line " + i));
        chat.scrollChat(4);
        chat.render(graphics, client.gui.getGuiTicks(), 10, 30);
        chat.resetChatScroll();
        chat.render(graphics, client.gui.getGuiTicks(), 10, 30);
        chat.clearMessages(false);

        var input = new EditBox(client.font, 0, 0, 200, 18, Component.literal("Smoke command"));
        input.setValue("auram:");
        var parent = new CommandSuggestions(client, client.screen, input, client.font, false, false, 1, 5, false, 0xFF252525);
        var constructor = CommandSuggestions.SuggestionsList.class.getDeclaredConstructor(
                CommandSuggestions.class, int.class, int.class, int.class, List.class, boolean.class);
        constructor.setAccessible(true);
        List<Suggestion> suggestions = new ArrayList<>();
        for (int i = 0; i < 15; i++) suggestions.add(new Suggestion(StringRange.at(6), "example" + i));
        var panel = constructor.newInstance(parent, 12, 12, 150, suggestions, true);
        panel.render(graphics, 0, 0);
        for (int i = 0; i < 8; i++) panel.cycle(1);
        panel.render(graphics, 0, 0);

        GameType mode = client.gameMode.getPlayerMode();
        boolean creativeScrolled = false;
        try {
            client.gameMode.setLocalMode(GameType.CREATIVE);
            var creative = new CreativeModeInventoryScreen(client.player, client.level.enabledFeatures(), client.options.operatorItemsTab().get());
            int width = graphics.guiWidth(), height = graphics.guiHeight();
            creative.init(client, width, height);
            try {
                creative.render(graphics, 0, 0, 0);
                creativeScrolled = creative.mouseScrolled(width / 2.0, height / 2.0, -1);
                creative.render(graphics, width / 2, height / 2, 0);
                require(creativeScrolled, "creative fixture tab has no rows to scroll");
            } finally {
                creative.removed();
            }
        } finally {
            client.gameMode.setLocalMode(mode);
        }
        graphics.flush();
        return Map.of("list_pixels", listPositions, "hotbar_wrap_both_directions", true, "chat_scroll_and_reset", true,
                "suggestion_cycle_and_render", true, "creative_grid_scrolled", creativeScrolled);
    }

    private static double[] selectionList(Minecraft client, GuiGraphics graphics) {
        var list = new ObjectSelectionList<ListRow>(client, 180, 140, 12, 112, 24) {
            {
                for (int i = 0; i < 20; i++) addEntry(new ListRow());
            }
        };
        list.render(graphics, 0, 0, 0);
        list.mouseScrolled(20, 20, -1);
        double first = list.getScrollAmount();
        list.render(graphics, 0, 0, 0);
        double step = list.getScrollAmount();
        for (int i = 0; i < 24; i++) list.render(graphics, 0, 0, 0);
        double end = list.getScrollAmount();
        require(first == 0 && step > first && end > step && end <= 30,
                "unexpected selection list motion " + first + ", " + step + ", " + end);
        list.setScrollAmount(Double.MAX_VALUE);
        require(list.getScrollAmount() == list.getMaxScroll(), "selection list did not clamp");
        list.setScrollAmount(0);
        require(list.getScrollAmount() == 0, "selection list did not reset");
        return new double[]{first, step, end};
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class ListRow extends ObjectSelectionList.Entry<ListRow> {
        @Override
        public Component getNarration() {
            return Component.literal("QA row");
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            graphics.drawString(Minecraft.getInstance().font, "QA row " + index, left, top, -1);
        }
    }
}

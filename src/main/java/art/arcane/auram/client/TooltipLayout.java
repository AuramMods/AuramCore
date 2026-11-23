package art.arcane.auram.client;

import art.arcane.auram.mixin.tweak.ClientTextTooltipAccess;
import art.arcane.auram.tweak.TweakConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TooltipLayout {
    private static final Settings SETTINGS = TweakConfig.read("tooltip-layout.json", Settings.class, new Settings());

    private TooltipLayout() {
    }

    public static boolean enabled() {
        return SETTINGS.enabled;
    }

    public static boolean useLeft(List<ClientTooltipComponent> components, Font font, int x, int screenWidth) {
        int right = screenWidth - x - 20;
        int left = x - 28;
        int customWidth = 0;
        for (var component : components) {
            if (!(component instanceof ClientTextTooltip)) customWidth = Math.max(customWidth, component.getWidth(font));
        }
        return (right < 100 || customWidth > right) && (left >= customWidth || left > right);
    }

    public static List<ClientTooltipComponent> wrap(List<ClientTooltipComponent> components, Font font, int x,
                                                      int screenWidth, boolean defaultPosition) {
        int width = defaultPosition
                ? useLeft(components, font, x, screenWidth) ? x - 28 : screenWidth - x - 20
                : screenWidth - 8;
        width = Math.max(1, width);
        List<ClientTooltipComponent> result = null;

        for (int i = 0; i < components.size(); i++) {
            ClientTooltipComponent component = components.get(i);
            if (component instanceof ClientTextTooltipAccess accessor) {
                FormattedCharSequence text = accessor.auram$getText();
                if (component.getWidth(font) > width || !text.accept((index, style, cp) -> cp != '\n' && cp != '\\')) {
                    if (result == null) result = new ArrayList<>(components.subList(0, i));
                    for (FormattedText line : font.getSplitter().splitLines(asFormattedText(text), width, Style.EMPTY)) {
                        // The incoming sequence is already in visual order, including shaped RTL glyphs.
                        result.add(ClientTooltipComponent.create(sink ->
                                line.visit((style, value) -> StringDecomposer.iterate(value, style, sink)
                                        ? Optional.empty() : FormattedText.STOP_ITERATION, Style.EMPTY).isEmpty()));
                    }
                    continue;
                }
            }
            if (result != null) result.add(component);
        }
        return result == null ? components : result;
    }

    private static FormattedText asFormattedText(FormattedCharSequence text) {
        List<FormattedText> parts = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        Style[] previous = {Style.EMPTY};
        text.accept((index, style, cp) -> {
            if (!style.equals(previous[0])) {
                append(parts, run, previous[0]);
                previous[0] = style;
            }
            run.appendCodePoint(cp);
            return true;
        });
        append(parts, run, previous[0]);
        return FormattedText.composite(parts);
    }

    private static void append(List<FormattedText> parts, StringBuilder run, Style style) {
        if (run.length() == 0) return;
        parts.add(FormattedText.of(run.toString().replace("\\n", "\n"), style));
        run.setLength(0);
    }

    private static final class Settings {
        private boolean enabled;
    }
}

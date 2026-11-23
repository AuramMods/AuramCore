package art.arcane.auram.smoke;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class TooltipLayoutSmoke {
    public static final class Samples {
        private final List<Map<String, Integer>> samples = new ArrayList<>();

        @SubscribeEvent
        public void sample(RenderTooltipEvent.Color event) {
            int width = event.getComponents().stream().mapToInt(part -> part.getWidth(event.getFont())).max().orElse(0);
            int text = (int) event.getComponents().stream().filter(ClientTextTooltip.class::isInstance).count();
            samples.add(Map.of("x", event.getX(), "y", event.getY(), "width", width,
                    "lines", text, "other", event.getComponents().size() - text));
        }
    }

    private TooltipLayoutSmoke() {
    }

    static List<Map<String, Integer>> run(Minecraft client) {
        GuiGraphics graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
        Samples events = new Samples();
        MinecraftForge.EVENT_BUS.register(events);
        try {
            var styled = Component.literal("Auram styled tooltip ").withStyle(ChatFormatting.RED)
                    .append(Component.literal("W".repeat(90) + "\n右から左 שלום 😀").withStyle(ChatFormatting.BOLD));
            graphics.renderTooltip(client.font, styled, 12, 30);
            graphics.renderTooltip(client.font, styled, graphics.guiWidth() - 12, 30);
            var lines = List.<Component>of(Component.literal("Auram"), styled);
            graphics.renderTooltip(client.font, lines, java.util.Optional.of(
                    new BundleTooltip(NonNullList.of(ItemStack.EMPTY, new ItemStack(Items.STONE)), 64)),
                    graphics.guiWidth() - 12, graphics.guiHeight() - 12);
            graphics.flush();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(events);
        }
        if (events.samples.size() != 3) throw new AssertionError("Expected three Forge tooltip color events: " + events.samples);
        for (var sample : events.samples) {
            if (sample.get("x") < 0 || sample.get("x") + sample.get("width") > graphics.guiWidth() || sample.get("lines") < 2) {
                throw new AssertionError("Tooltip overflow or missing lines: " + sample);
            }
        }
        if (events.samples.get(2).get("other") < 1) throw new AssertionError("Missing tooltip image");
        return List.copyOf(events.samples);
    }
}

package art.arcane.auram.client.menu;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.MinecraftUiDraw;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.auram.client.menu.AdaptSkillTree.Node;
import art.arcane.auram.client.menu.AdaptSkillTree.Viewport;
import art.arcane.auram.client.menu.SkillTreeLayout.Camera;
import art.arcane.auram.client.menu.SkillTreeLayout.Point;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Objects;

final class SkillTreeRenderer {
    private static final int GOLD = 0xfff1c88f;
    private static final int MUTED = 0xff748495;
    private static final int SIDES = 32;
    private static final MinecraftUiDraw icons = new MinecraftUiDraw();

    private SkillTreeRenderer() {}

    static void draw(GuiGraphics graphics, Viewport viewport, Camera camera, List<Node> nodes, Route selection,
                     String query, Node hovered, boolean root, boolean waiting, int mouseX, int mouseY) {
        var client = Minecraft.getInstance();
        graphics.fillGradient(0, AuramMenu.BAR_HEIGHT, viewport.width(), graphics.guiHeight(), 0xca101724, 0xf0060d17);
        graphics.fill(0, viewport.top() - 1, viewport.width(), viewport.top(), 0x43597282);
        graphics.fill(0, viewport.bottom(), viewport.width(), viewport.bottom() + 1, 0x43597282);
        if (viewport.bottom() <= viewport.top()) return;
        graphics.enableScissor(0, viewport.top(), viewport.width(), viewport.bottom());
        graphics.flush();
        var mesh = new Mesh(graphics);
        Point center = screen(camera, viewport, AdaptSkillTree.CENTER);
        for (int r = 32; r <= 704; r += 48) {
            double size = r * camera.scale();
            if (size > 12) mesh.ring(center, size, size + 0.65, r == 32 ? 0x295a768d : 0x18577991);
        }
        for (Node node : nodes) {
            if (node.parent() == null || !node.matches(query)) continue;
            Point start = screen(camera, viewport, node.parent());
            Point end = screen(camera, viewport, node.point());
            if (!edgeVisible(viewport, start, end)) continue;
            if (node.level() > 0) {
                mesh.line(start, end, node.skill() ? 2.0 : 1.55, withAlpha(node.color(), 203));
            } else if (node.skill()) {
                mesh.line(start, end, 0.8, 0x68588398);
            } else {
                mesh.dashed(start, end, 0.8, 0x7973828f);
            }
        }
        for (Node node : nodes) {
            if (!node.matches(query)) continue;
            Point pos = screen(camera, viewport, node.point());
            double r = node.radius(camera.scale());
            if (!visible(viewport, pos, r + 4)) continue;
            boolean focused = node == hovered;
            boolean selected = selection != null && Objects.equals(node.route(), selection);
            boolean learned = node.level() > 0;
            int rim = node.route() == null ? GOLD : learned ? glow(node.color()) : MUTED;
            mesh.disc(pos, r + 2.2, 0xa0050a13);
            mesh.disc(pos, r, node.skill() ? 0xff192a38 : learned ? 0xff203240 : 0xff242b37);
            mesh.ring(pos, r - 1.0, r + 0.1, rim);
            if (node.progress() > 0) {
                mesh.arc(pos, r + 0.9, r + 2.2, Mth.clamp(node.progress(), 0, 1), rim);
            }
            if (focused || selected) {
                mesh.ring(pos, r + 2.9, r + (selected ? 4.7 : 4.0), selected ? GOLD : 0xffc8f1f0);
            }
        }
        graphics.flush();

        var draw = icons.bind(graphics);
        for (Node node : nodes) {
            if (!node.matches(query)) continue;
            Point pos = screen(camera, viewport, node.point());
            double r = node.radius(camera.scale());
            if (!visible(viewport, pos, r + 4)) continue;
            int size = (int) (r * 1.32);
            int x = (int) Math.round(pos.x() - size * 0.5);
            int y = (int) Math.round(pos.y() - size * 0.5);
            if (node.route() == null && client.player != null) {
                PlayerFaceRenderer.draw(graphics, client.player.getSkinTextureLocation(), x, y, size);
            } else if (node.icon() != null) {
                UiIcons.paint(draw, node.icon(), x, y, size, false);
                if (!node.skill() && node.level() == 0 && node != hovered && !Objects.equals(node.route(), selection)) {
                    graphics.fill(x, y, x + size, y + size, 0x6e131923);
                }
            }
            if (node.skill() && node.route() != null && camera.scale() >= 0.80 && root) {
                graphics.drawCenteredString(client.font, node.name(), (int) pos.x(),
                        (int) (pos.y() + r + 4), node.level() > 0 ? 0xffd6ebee : 0xffbcc9d4);
            }
        }
        graphics.flush();
        if (!root) graphics.fill(0, viewport.top(), viewport.width(), viewport.bottom(), 0x78080c15);
        graphics.disableScissor();

        if (root) {
            var hint = Component.translatable("gui.auram.skills.navigation");
            graphics.drawString(client.font, hint, 12, viewport.bottom() + 7, 0xffadbfca, false);
            boolean resetHovered = viewport.recenterContains(mouseX, mouseY);
            int resetX = viewport.recenterX();
            int resetY = viewport.bottom() + 3;
            int resetEnd = viewport.width() - 12;
            graphics.fill(resetX, resetY, resetEnd, resetY + 16, resetHovered ? 0xff304653 : 0xff1b2a36);
            graphics.hLine(resetX, resetEnd - 1, resetY, resetHovered ? 0xff92c6c8 : 0xff3e5868);
            graphics.drawCenteredString(client.font, Component.translatable("gui.auram.skills.recenter"),
                    (resetX + resetEnd) / 2, resetY + 4, 0xffd9e9e9);
            if (waiting || nodes.size() <= 1
                    || !query.isEmpty() && nodes.stream().noneMatch(node -> node.route() != null && node.matches(query))) {
                String key = waiting ? "gui.auram.menu.opening_skills"
                        : nodes.size() <= 1 ? "gui.auram.skills.no_skills" : "gui.auram.skills.no_results";
                graphics.drawCenteredString(client.font, Component.translatable(key), viewport.width() / 2,
                        viewport.top() + 8, 0xffcfdbdf);
            }
        }
        if (hovered != null) tooltip(graphics, hovered, mouseX, mouseY);
    }

    private static void tooltip(GuiGraphics graphics, Node node, int x, int y) {
        var font = Minecraft.getInstance().font;
        Component name = Component.literal(node.name()).withStyle(ChatFormatting.GOLD);
        Component level = node.route() == null ? Component.translatable("gui.auram.skills.master_level", node.level())
                : node.skill() ? Component.translatable("gui.auram.skills.level", node.level())
                : Component.translatable("gui.auram.skills.adaptation_level", node.level(), node.maxLevel());
        Component state = node.route() == null ? Component.translatable("gui.auram.skills.reset")
                : !node.openable() ? Component.translatable("gui.auram.skills.unavailable")
                : !node.skill() && node.level() == 0 ? Component.translatable("gui.auram.skills.unlearned")
                : null;
        List<FormattedCharSequence> lines = state == null ? List.of(name.getVisualOrderText(), level.getVisualOrderText())
                : List.of(name.getVisualOrderText(), level.getVisualOrderText(),
                        state.copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
        graphics.renderTooltip(font, lines, x, y);
    }

    private static Point screen(Camera camera, Viewport viewport, Point point) {
        return camera.toScreen(point, viewport.cx(), viewport.cy());
    }

    private static boolean visible(Viewport v, Point p, double padding) {
        return p.x() >= -padding && p.x() < v.width() + padding
                && p.y() >= v.top() - padding && p.y() < v.bottom() + padding;
    }

    private static boolean edgeVisible(Viewport v, Point a, Point b) {
        return Math.max(a.x(), b.x()) >= 0 && Math.min(a.x(), b.x()) < v.width()
                && Math.max(a.y(), b.y()) >= v.top() && Math.min(a.y(), b.y()) < v.bottom();
    }

    private static int glow(int color) {
        return 0xff000000 | brighten(color >> 16 & 255) << 16 | brighten(color >> 8 & 255) << 8 | brighten(color & 255);
    }

    private static int brighten(int channel) {
        return (channel * 2 + 255) / 3;
    }

    private static int withAlpha(int color, int alpha) {
        return alpha << 24 | color & 0xffffff;
    }

    private static final class Mesh {
        private final VertexConsumer vertices;
        private final Matrix4f pose;

        private Mesh(GuiGraphics graphics) {
            vertices = graphics.bufferSource().getBuffer(RenderType.gui());
            pose = graphics.pose().last().pose();
        }

        private void disc(Point center, double r, int color) {
            arc(center, 0, r, 1.0, color);
        }

        private void ring(Point center, double r, double outer, int color) {
            arc(center, r, outer, 1.0, color);
        }

        private void arc(Point center, double inner, double outer, double progress, int color) {
            double full = 2 * Math.PI * progress;
            int sides = Math.max(1, (int) Math.ceil(SIDES * progress));
            double x = 0, y = -1;
            for (int i = 1; i <= sides; i++) {
                double angle = full * i / sides - Math.PI / 2;
                double nextX = Math.cos(angle);
                double nextY = Math.sin(angle);
                vertex(center.x() + inner * x, center.y() + inner * y, color);
                vertex(center.x() + inner * nextX, center.y() + inner * nextY, color);
                vertex(center.x() + outer * nextX, center.y() + outer * nextY, color);
                vertex(center.x() + outer * x, center.y() + outer * y, color);
                x = nextX;
                y = nextY;
            }
        }

        private void line(Point a, Point b, double thickness, int color) {
            double length = a.distance(b);
            if (length <= 0.001) return;
            double dx = -(b.y() - a.y()) * thickness / (2 * length);
            double dy = (b.x() - a.x()) * thickness / (2 * length);
            vertex(a.x() - dx, a.y() - dy, color);
            vertex(a.x() + dx, a.y() + dy, color);
            vertex(b.x() + dx, b.y() + dy, color);
            vertex(b.x() - dx, b.y() - dy, color);
        }

        private void dashed(Point a, Point b, double thickness, int color) {
            double length = a.distance(b);
            for (double start = 0; start < length; start += 9.0) {
                double end = Math.min(start + 4.5, length);
                line(new Point(Mth.lerp(start / length, a.x(), b.x()), Mth.lerp(start / length, a.y(), b.y())),
                        new Point(Mth.lerp(end / length, a.x(), b.x()), Mth.lerp(end / length, a.y(), b.y())), thickness, color);
            }
        }

        private void vertex(double x, double y, int color) {
            vertices.vertex(pose, (float) x, (float) y, 0).color(color >> 16 & 255, color >> 8 & 255,
                    color & 255, color >>> 24).endVertex();
        }
    }
}

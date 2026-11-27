package art.arcane.auram.client.menu;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

final class SkillTreeLayout {
    record Point(double x, double y) {
        double distance(Point other) {
            return Math.hypot(x - other.x, y - other.y);
        }

        private static Point polar(double radius, double angle) {
            return new Point(Math.cos(angle) * radius, Math.sin(angle) * radius);
        }
    }

    record Branch(Point skill, List<Point> adaptations) {}

    private SkillTreeLayout() {}

    static List<Branch> branches(List<Integer> counts) {
        if (counts.isEmpty()) return List.of();
        List<Branch> branches = new ArrayList<>(counts.size());
        double wedge = 2 * Math.PI / counts.size();
        double skillRadius = Math.max(96, counts.size() * 55 / (2 * Math.PI));
        double fan = Math.min(wedge * 0.40, Math.PI / 4);
        for (int skill = 0; skill < counts.size(); skill++) {
            double angle = -Math.PI / 2 + skill * wedge;
            var adaptations = new ArrayList<Point>(counts.get(skill));
            double radius = skillRadius + 62;
            while (adaptations.size() < counts.get(skill)) {
                int capacity = 1 + (int) (2 * radius * Math.sin(fan) / 33);
                int count = Math.min(Math.min(7, capacity), counts.get(skill) - adaptations.size());
                double step = count == 1 ? 0 : Math.min(2 * fan / (count - 1), 39 / radius);
                for (int i = 0; i < count; i++) {
                    adaptations.add(Point.polar(radius, angle + (i - (count - 1) / 2.0) * step));
                }
                radius += 48;
            }
            branches.add(new Branch(Point.polar(skillRadius, angle), List.copyOf(adaptations)));
        }
        return List.copyOf(branches);
    }

    static final class Camera {
        private double offsetX;
        private double offsetY;
        private double scale = 1;
        private boolean initialized;

        void fit(int width, int height, double extentX, double extentY) {
            offsetX = 0;
            offsetY = 0;
            scale = Mth.clamp(Math.min(width / (2 * extentX + 68), height / (2 * extentY + 68)), 0.20, 1.0);
            initialized = true;
        }

        boolean initialized() {
            return initialized;
        }

        double scale() {
            return scale;
        }

        Point toScreen(Point point, double centerX, double centerY) {
            return new Point(centerX + offsetX + point.x * scale, centerY + offsetY + point.y * scale);
        }

        void pan(double dx, double dy) {
            offsetX += dx;
            offsetY += dy;
        }

        void zoom(double x, double y, double centerX, double centerY, double wheel) {
            double next = Mth.clamp(scale * Math.pow(1.18, wheel), 0.20, 3.2);
            double ratio = next / scale;
            offsetX = x - centerX - (x - centerX - offsetX) * ratio;
            offsetY = y - centerY - (y - centerY - offsetY) * ratio;
            scale = next;
        }
    }
}

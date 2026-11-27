package art.arcane.auram.client.menu;

import art.arcane.auram.map.client.MapAnimation;
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

    record Anchor(Placement placement, Point seat, double radius) {}

    static List<Point> spread(List<Anchor> anchors) {
        int size = anchors.size();
        double[] x = new double[size];
        double[] y = new double[size];
        for (int i = 0; i < size; i++) {
            x[i] = anchors.get(i).seat().x;
            y[i] = anchors.get(i).seat().y;
        }

        // Pull gently toward the radial seats, then finish resolving overlaps without the pull.
        // The player at index zero stays fixed. Extra radius around unlocked nodes gives them room.
        for (int pass = 0; pass < 48; pass++) {
            if (pass < 32) {
                for (int i = 1; i < size; i++) {
                    Point seat = anchors.get(i).seat();
                    x[i] = Mth.lerp(0.08, x[i], seat.x);
                    y[i] = Mth.lerp(0.08, y[i], seat.y);
                }
            }
            for (int i = 0; i < size; i++) {
                for (int j = i + 1; j < size; j++) {
                    double dx = x[j] - x[i];
                    double dy = y[j] - y[i];
                    double gap = anchors.get(i).radius() + anchors.get(j).radius();
                    double squared = dx * dx + dy * dy;
                    if (squared >= gap * gap) continue;
                    double distance = Math.sqrt(squared);
                    double angle = (i + j) * 2.399963229728653;
                    double push = gap - distance;
                    double pushX = push * (distance > 0 ? dx / distance : Math.cos(angle));
                    double pushY = push * (distance > 0 ? dy / distance : Math.sin(angle));
                    double share = i == 0 ? 0 : 0.5;
                    x[i] -= pushX * share;
                    y[i] -= pushY * share;
                    x[j] += pushX * (1 - share);
                    y[j] += pushY * (1 - share);
                }
            }
        }

        List<Point> positions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) positions.add(new Point(x[i], y[i]));
        return positions;
    }

    static final class Placement {
        private Point point;
        private Point start;
        private Point target;
        private long startedAt;

        Placement(Point point) {
            this.point = target = point;
        }

        Point point() {
            return point;
        }

        void moveTo(Point next, long now) {
            if (next.equals(target)) return;
            start = point;
            target = next;
            startedAt = now;
        }

        void advance(long now) {
            if (point.equals(target)) return;
            double progress = MapAnimation.easeOut(now - startedAt);
            point = progress == 1 ? target : new Point(Mth.lerp(progress, start.x, target.x),
                    Mth.lerp(progress, start.y, target.y));
        }
    }

    static final class Camera {
        private static final double MIN_SCALE = 0.20;
        private static final double MAX_SCALE = 3.2;

        private double offsetX;
        private double offsetY;
        private double scale = 1;
        private double targetScale = 1;
        private double startScale;
        private double startX;
        private double startY;
        private double targetX;
        private double targetY;
        private long startedAt;
        private boolean moving;
        private boolean initialized;

        void fit(int width, int height, double extentX, double extentY, long now) {
            double next = Mth.clamp(Math.min(width / (2 * extentX + 68), height / (2 * extentY + 68)),
                    MIN_SCALE, 1.0);
            if (!initialized) {
                scale = targetScale = next;
                initialized = true;
            } else {
                animateTo(next, 0, 0, now);
            }
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
            startX += dx;
            startY += dy;
            targetX += dx;
            targetY += dy;
        }

        void zoom(double x, double y, double centerX, double centerY, double wheel, long now) {
            advance(now);
            double next = Mth.clamp(targetScale * Math.pow(1.18, wheel), MIN_SCALE, MAX_SCALE);
            if (next == targetScale) return;
            double ratio = next / scale;
            animateTo(next, x - centerX - (x - centerX - offsetX) * ratio,
                    y - centerY - (y - centerY - offsetY) * ratio, now);
        }

        private void animateTo(double next, double x, double y, long now) {
            startScale = scale;
            startX = offsetX;
            startY = offsetY;
            targetScale = next;
            targetX = x;
            targetY = y;
            startedAt = now;
            moving = true;
        }

        void advance(long now) {
            if (!moving) return;
            double progress = MapAnimation.easeOut(now - startedAt);
            if (progress == 1) {
                scale = targetScale;
                offsetX = targetX;
                offsetY = targetY;
                moving = false;
                return;
            }
            scale = startScale * Math.pow(targetScale / startScale, progress);
            // Camera translation follows the displayed scale so the world point under the cursor stays put.
            double offsetProgress = startScale == targetScale ? progress : (scale - startScale) / (targetScale - startScale);
            offsetX = Mth.lerp(offsetProgress, startX, targetX);
            offsetY = Mth.lerp(offsetProgress, startY, targetY);
        }
    }
}

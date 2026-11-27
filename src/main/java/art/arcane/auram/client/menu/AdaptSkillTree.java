package art.arcane.auram.client.menu;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.screen.AdaptScreen;
import art.arcane.adapt.client.screen.CatalogIndex;
import art.arcane.adapt.client.screen.ScreenRouter;
import art.arcane.adapt.client.screen.SkillsScreen;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.auram.client.menu.SkillTreeLayout.Anchor;
import art.arcane.auram.client.menu.SkillTreeLayout.Placement;
import art.arcane.auram.client.menu.SkillTreeLayout.Point;
import art.arcane.auram.map.client.FullscreenMapFling;
import art.arcane.auram.mixin.adapt.AdaptScreenAccess;
import art.arcane.auram.mixin.adapt.AdaptSkillsAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class AdaptSkillTree {
    static final Point CENTER = new Point(0, 0);
    private static final int MARGIN = 12;
    private static final int FOOT = 22;
    private static final long REFRESH_NANOS = 125_000_000L;
    private static Session session = new Session();

    private AdaptSkillTree() {}

    public static void layoutToolbar(SkillsScreen skills, UiDraw draw) {
        UiNode root = skills.root();
        UiNode frame = root.find("frame");
        UiNode chips = root.find("chips");
        var search = skills.search();
        var snackbar = skills.snackbar();
        if (chips.parent() != root) {
            root.add(chips);
            root.add(search);
            root.add(snackbar);
            frame.setVisible(false);
        }

        int chipsWidth = 0;
        for (UiNode item : chips.children()) {
            if (item.visible()) chipsWidth += (chipsWidth == 0 ? 0 : 2) + item.preferredWidth(draw);
        }
        int right = skills.width() - MARGIN;
        int top = AuramMenu.BAR_HEIGHT + 8;
        chips.place(new UiRect(Math.max(MARGIN, right - chipsWidth), top, Math.min(right - MARGIN, chipsWidth), 16), draw);

        int searchRight = skills.width() < 245 ? right : right - chipsWidth - 6;
        int searchWidth = Math.min(104, searchRight - MARGIN);
        search.place(new UiRect(searchRight - searchWidth, top + (skills.width() < 245 ? 22 : 0), searchWidth, 16), draw);
        snackbar.place(new UiRect(MARGIN, Math.max(top + 18, footerTop(skills.height()) - 22),
                Math.min(220, skills.width() - 2 * MARGIN), 18), draw);
    }

    static void reset() {
        session = new Session();
    }

    static void suspend() {
        session.cancelGesture();
        session.fling.stop();
    }

    static boolean editingText(Screen screen) {
        AdaptScreen adapt = content(screen);
        return adapt != null && adapt.editingText();
    }

    static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY) {
        AdaptScreen adapt = content(screen);
        if (adapt == null) return;
        ScreenRouter router = router(adapt);
        boolean root = adapt instanceof SkillsScreen;
        if (!root) session.selection = adapt.route();
        if (!root || !adapt.overlays().isEmpty()) suspend();
        var viewport = viewport(screen);
        session.update(adapt, router, viewport);
        SkillTreeRenderer.draw(graphics, viewport, session.camera, session.nodes, session.selection,
                session.query, root && adapt.overlays().isEmpty() ? hit(viewport, mouseX, mouseY) : null,
                root, session.waiting, mouseX, mouseY);
    }

    static boolean press(Screen screen, double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && viewport(screen).recenterContains(x, y)
                && content(screen) instanceof SkillsScreen skills && skills.overlays().isEmpty()) {
            skills.focus(null);
            recenter(skills, viewport(screen));
            return true;
        }
        if (!interactive(screen, x, y)) return false;
        var skills = (SkillsScreen) content(screen);
        skills.focus(null);
        session.button = button;
        session.pressX = x;
        session.pressY = y;
        session.pendingX = 0;
        session.pendingY = 0;
        session.dragging = false;
        session.fling.begin(System.nanoTime());
        return true;
    }

    static boolean drag(Screen screen, int button, double dx, double dy) {
        if (session.button != button || !(content(screen) instanceof SkillsScreen)) return false;
        if (!session.dragging) {
            session.pendingX += dx;
            session.pendingY += dy;
            if (Math.hypot(session.pendingX, session.pendingY) < 3) return true;
            session.dragging = true;
            session.manualView = true;
            dx = session.pendingX;
            dy = session.pendingY;
        }
        session.camera.pan(dx, dy);
        session.fling.drag(System.nanoTime(), dx, dy);
        return true;
    }

    static boolean release(Screen screen, double x, double y, int button) {
        if (session.button != button) return false;
        boolean click = !session.dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && Math.hypot(x - session.pressX, y - session.pressY) < 4;
        if (session.dragging) session.fling.release(System.nanoTime());
        session.cancelGesture();
        if (click && interactive(screen, x, y)) {
            var skills = (SkillsScreen) content(screen);
            var viewport = viewport(screen);
            Node node = hit(viewport, x, y);
            if (node != null && node.route() == null) {
                recenter(skills, viewport);
            } else if (node != null) {
                session.selection = node.route();
                skills.services().playClick();
                router(skills).open(node.route());
            }
        }
        return true;
    }

    static boolean scroll(Screen screen, double x, double y, double amount) {
        if (!interactive(screen, x, y)) return false;
        var view = viewport(screen);
        session.manualView = true;
        session.fling.stop();
        session.camera.zoom(x, y, view.cx(), view.cy(), amount, System.nanoTime());
        return true;
    }

    private static void recenter(SkillsScreen skills, Viewport view) {
        session.manualView = false;
        session.fling.stop();
        session.fit(view, System.nanoTime());
        session.selection = null;
        skills.services().playClick();
    }

    private static AdaptScreen content(Screen screen) {
        Object content = MenuIntegrations.skillContent(screen);
        return content instanceof AdaptScreen adapt ? adapt : null;
    }

    private static ScreenRouter router(AdaptScreen adapt) {
        return ((AdaptScreenAccess) adapt).auram$router();
    }

    private static boolean interactive(Screen screen, double x, double y) {
        return content(screen) instanceof SkillsScreen skills && skills.overlays().isEmpty()
                && viewport(screen).contains(x, y);
    }

    private static Node hit(Viewport viewport, double x, double y) {
        if (!viewport.contains(x, y)) return null;
        Node best = null;
        double distance = Double.MAX_VALUE;
        for (Node node : session.nodes) {
            if (!node.matches(session.query)) continue;
            var screen = session.camera.toScreen(node.point(), viewport.cx(), viewport.cy());
            double delta = Math.hypot(x - screen.x(), y - screen.y());
            if (delta <= node.radius(session.camera.scale()) + 2 && delta < distance) {
                best = node;
                distance = delta;
            }
        }
        return best;
    }

    public static int footerTop(int height) {
        return height - FOOT;
    }

    private static Viewport viewport(Screen screen) {
        return new Viewport(screen.width, screen.width < 245 ? AuramMenu.BAR_HEIGHT + 54 : AuramMenu.BAR_HEIGHT + 32,
                Math.max(AuramMenu.BAR_HEIGHT + 56, footerTop(screen.height)));
    }

    record Viewport(int width, int top, int bottom) {
        double cx() { return width * 0.5; }
        double cy() { return (top + bottom) * 0.5; }
        boolean contains(double x, double y) { return x >= 0 && x < width && y >= top && y < bottom; }
        int recenterX() { return width - 72; }
        boolean recenterContains(double x, double y) {
            return x >= recenterX() && x < width - MARGIN && y >= bottom + 3 && y < bottom + 19;
        }
    }

    record Node(Route route, Placement placement, Placement parent, String name,
                String searchText, IconRef icon, int color, int level, int maxLevel, double progress, boolean skill) {
        boolean matches(String query) {
            return route == null || searchText.contains(query);
        }

        Point point() {
            return placement.point();
        }

        double radius(double scale) {
            return Math.min(route == null ? 31 : skill ? 24 : 18, baseRadius() * scale);
        }

        double spacingRadius() {
            return baseRadius() + 6 + (route != null && level > 0 ? 7 : 0);
        }

        private int baseRadius() {
            return route == null ? 24 : skill ? 17 : 13;
        }
    }

    private record NodeKey(String branch, Route route) {}

    private static final class Session {
        private final SkillTreeLayout.Camera camera = new SkillTreeLayout.Camera();
        private final FullscreenMapFling fling = new FullscreenMapFling();
        private final Placement center = new Placement(CENTER);
        private List<Node> nodes = List.of();
        private List<Anchor> anchors = List.of();
        private Map<NodeKey, Placement> placements = Map.of();
        private List<SkillView> seats = List.of();
        private PlayerView lastPlayer;
        private CatalogView lastCatalog;
        private Map<String, String> lastShortNames;
        private Route selection;
        private String query = "";
        private boolean waiting = true;
        private double extentX = 96;
        private double extentY = 96;
        private long nextUpdate = Long.MIN_VALUE;
        private int button = -1;
        private double pressX;
        private double pressY;
        private double pendingX;
        private double pendingY;
        private boolean dragging;
        private boolean manualView;
        private Viewport fittedViewport;

        private void update(AdaptScreen adapt, ScreenRouter router, Viewport viewport) {
            query = adapt instanceof SkillsScreen skills
                    ? skills.search().text().strip().toLowerCase(Locale.ROOT) : "";

            long now = System.nanoTime();
            camera.advance(now);
            var offset = fling.advance(now);
            camera.pan(offset.x(), offset.y());
            for (Anchor anchor : anchors) anchor.placement().advance(now);

            if (now >= nextUpdate) {
                nextUpdate = now + REFRESH_NANOS;
                refresh(adapt, router, now);
            }
            if (!manualView && !viewport.equals(fittedViewport)) fit(viewport, now);
        }

        private void refresh(AdaptScreen adapt, ScreenRouter router, long now) {
            PlayerView player = router.state().player();
            var catalog = router.state().catalog();
            if (player == null || catalog == null) return;
            waiting = false;

            List<SkillView> visible = seats;
            if (adapt instanceof SkillsScreen skills) {
                visible = ((AdaptSkillsAccess) (Object) skills).auram$visibleSkills();
            } else if (seats.isEmpty()) {
                // Adapt can open a detail route before it builds the Skills root.
                visible = catalog.skills().stream()
                        .sorted(Comparator.comparing(skill -> LegacyText.strip(skill.name()), String.CASE_INSENSITIVE_ORDER))
                        .toList();
            }

            Map<String, String> names = router.state().shortNames();
            if (player == lastPlayer && catalog == lastCatalog && names == lastShortNames && visible.equals(seats)) return;
            lastPlayer = player;
            lastCatalog = catalog;
            lastShortNames = names;
            seats = visible;

            CatalogIndex index = new CatalogIndex(catalog, names);
            List<SkillView> discovered = visible.stream().filter(skill -> SkillTreeProgression.discovered(player, skill)).toList();
            List<List<AdaptationView>> children = new ArrayList<>(discovered.size());
            for (SkillView skill : discovered) {
                children.add(SkillTreeProgression.reveal(player, index.groups(player, skill.id())).stream()
                        .flatMap(List::stream).distinct().toList());
            }
            var branches = SkillTreeLayout.branches(children.stream().map(List::size).toList());
            List<Node> next = new ArrayList<>();
            List<Anchor> nextAnchors = new ArrayList<>();
            Map<NodeKey, Placement> nextPlacements = new HashMap<>();
            String playerName = Minecraft.getInstance().player.getName().getString();
            var root = new Node(null, center, null, playerName, "", null, 0xffdaa56d, player.header().masterLevel(), 0,
                    player.header().masterProgress(), true);
            next.add(root);
            nextAnchors.add(new Anchor(center, CENTER, root.spacingRadius()));
            for (int i = 0; i < discovered.size(); i++) {
                var skill = discovered.get(i);
                var line = CatalogIndex.line(player, skill.id());
                var branch = branches.get(i);
                String search = searchable(skill.name(), skill.id(), skill.description());
                int color = 0xff000000 | skill.color();
                List<AdaptationView> adaptations = children.get(i);
                String skillSearch = search + " " + adaptations.stream()
                        .map(a -> searchable(a.name(), a.id(), a.description())).collect(Collectors.joining(" "));

                var route = new Route.Skill(skill.id());
                Placement skillPosition = place(nextPlacements, skill.id(), route, branch.skill());
                var skillNode = new Node(route, skillPosition, center, LegacyText.strip(skill.name()),
                        skillSearch, skill.icon(), color, line == null ? 0 : line.level(), 0,
                        line == null ? 0 : line.progress(), true);
                next.add(skillNode);
                nextAnchors.add(new Anchor(skillPosition, branch.skill(), skillNode.spacingRadius()));

                for (int j = 0; j < adaptations.size(); j++) {
                    var adaptation = adaptations.get(j);
                    var point = branch.adaptations().get(j);
                    var adaptationRoute = new Route.Adaptation(adaptation.id());
                    Placement position = place(nextPlacements, skill.id(), adaptationRoute, point);
                    var node = new Node(adaptationRoute, position, skillPosition, LegacyText.strip(adaptation.name()),
                            search + " " + searchable(adaptation.name(), adaptation.id(), adaptation.description()),
                            adaptation.icon(), color, CatalogIndex.level(player, adaptation.id()), adaptation.maxLevel(), 0, false);
                    next.add(node);
                    nextAnchors.add(new Anchor(position, point, node.spacingRadius()));
                }
            }
            nodes = List.copyOf(next);
            placements = nextPlacements;
            if (anchors.equals(nextAnchors)) return;
            anchors = nextAnchors;

            List<Point> targets = SkillTreeLayout.spread(anchors);
            extentX = 96;
            extentY = 96;
            for (int i = 0; i < targets.size(); i++) {
                Point point = targets.get(i);
                Anchor anchor = anchors.get(i);
                anchor.placement().moveTo(point, now);
                extentX = Math.max(extentX, Math.abs(point.x()) + anchor.radius());
                extentY = Math.max(extentY, Math.abs(point.y()) + anchor.radius());
            }
            fittedViewport = null;
        }

        private Placement place(Map<NodeKey, Placement> next, String branch, Route route, Point point) {
            var key = new NodeKey(branch, route);
            Placement position = placements.get(key);
            if (position == null) position = new Placement(point);
            next.put(key, position);
            return position;
        }

        private static String searchable(String name, String id, String description) {
            return LegacyText.strip(name + " " + id + " " + description).toLowerCase(Locale.ROOT);
        }

        private void fit(Viewport viewport, long now) {
            camera.fit(viewport.width(), viewport.bottom() - viewport.top(), extentX, extentY, now);
            fittedViewport = viewport;
        }

        private void cancelGesture() {
            button = -1;
            dragging = false;
        }
    }
}

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
import art.arcane.auram.mixin.adapt.AdaptScreenAccess;
import art.arcane.auram.mixin.adapt.AdaptSkillsAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class AdaptSkillTree {
    static final SkillTreeLayout.Point CENTER = new SkillTreeLayout.Point(0, 0);
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
        snackbar.place(new UiRect(MARGIN, Math.max(top + 18, skills.height() - FOOT - 22),
                Math.min(220, skills.width() - 2 * MARGIN), 18), draw);
    }

    static void reset() {
        session = new Session();
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
        if (!root) {
            session.selection = adapt.route();
            session.cancelGesture();
        }
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
        return true;
    }

    static boolean drag(Screen screen, int button, double dx, double dy) {
        if (session.button != button || !(content(screen) instanceof SkillsScreen)) return false;
        if (session.dragging) {
            session.camera.pan(dx, dy);
        } else {
            session.pendingX += dx;
            session.pendingY += dy;
            if (Math.hypot(session.pendingX, session.pendingY) >= 3) {
                session.dragging = true;
                session.manualView = true;
                session.camera.pan(session.pendingX, session.pendingY);
            }
        }
        return true;
    }

    static boolean release(Screen screen, double x, double y, int button) {
        if (session.button != button) return false;
        boolean click = !session.dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && Math.hypot(x - session.pressX, y - session.pressY) < 4;
        session.cancelGesture();
        if (click && interactive(screen, x, y)) {
            var skills = (SkillsScreen) content(screen);
            var viewport = viewport(screen);
            Node node = hit(viewport, x, y);
            if (node != null && node.route() == null) {
                recenter(skills, viewport);
            } else if (node != null && node.openable()) {
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
        session.camera.zoom(x, y, view.cx(), view.cy(), amount);
        return true;
    }

    private static void recenter(SkillsScreen skills, Viewport view) {
        session.manualView = false;
        session.fit(view);
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

    private static Viewport viewport(Screen screen) {
        return new Viewport(screen.width, screen.width < 245 ? AuramMenu.BAR_HEIGHT + 54 : AuramMenu.BAR_HEIGHT + 32,
                Math.max(AuramMenu.BAR_HEIGHT + 56, screen.height - FOOT));
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

    record Node(Route route, SkillTreeLayout.Point point, SkillTreeLayout.Point parent, String name,
                String searchText, IconRef icon, int color, int level, int maxLevel, double progress,
                boolean openable, boolean skill) {
        boolean matches(String query) {
            return route == null || searchText.contains(query);
        }

        double radius(double scale) {
            return Math.max(route == null ? 10 : skill ? 7 : 5,
                    Math.min(route == null ? 31 : skill ? 24 : 18, (route == null ? 24 : skill ? 17 : 13) * scale));
        }
    }

    private static final class Session {
        private final SkillTreeLayout.Camera camera = new SkillTreeLayout.Camera();
        private List<Node> nodes = List.of();
        private List<SkillView> seats = List.of();
        private PlayerView lastPlayer;
        private CatalogView lastCatalog;
        private Map<String, String> lastShortNames;
        private Route selection;
        private String query = "";
        private boolean waiting = true;
        private double extentX = 260;
        private double extentY = 260;
        private long nextUpdate;
        private int button = -1;
        private double pressX;
        private double pressY;
        private double pendingX;
        private double pendingY;
        private boolean dragging;
        private boolean manualView;
        private Viewport fittedViewport;

        private void update(AdaptScreen adapt, ScreenRouter router, Viewport viewport) {
            boolean root = adapt instanceof SkillsScreen;
            if (root) query = ((SkillsScreen) adapt).search().text().strip().toLowerCase(Locale.ROOT);
            else query = "";

            long now = System.nanoTime();
            if (!manualView && !viewport.equals(fittedViewport)) fit(viewport);
            if (now < nextUpdate && camera.initialized()) return;
            nextUpdate = now + REFRESH_NANOS;
            PlayerView player = router.state().player();
            var catalog = router.state().catalog();
            if (player == null || catalog == null) return;
            waiting = false;
            List<SkillView> visible = seats;
            if (adapt instanceof SkillsScreen skills) {
                visible = ((AdaptSkillsAccess) (Object) skills).auram$visibleSkills();
            } else if (seats.isEmpty()) {
                // Adapt can enter a detail route without ever showing the Skills root.
                visible = catalog.skills().stream().filter(skill -> skill.enabled() && player.access().canOpen(skill.id()))
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
            List<List<AdaptationView>> children = new ArrayList<>(seats.size());
            for (SkillView skill : seats) {
                var line = CatalogIndex.line(player, skill.id());
                children.add(line == null || line.level() < 1 ? List.of()
                        : index.groups(player, skill.id()).stream().flatMap(List::stream).distinct().toList());
            }
            var branches = SkillTreeLayout.branches(children.stream().map(List::size).toList());
            List<Node> next = new ArrayList<>();
            String playerName = Minecraft.getInstance().player.getName().getString();
            next.add(new Node(null, CENTER, null, playerName, "", null, 0xffdaa56d, player.header().masterLevel(), 0,
                    player.header().masterProgress(), true, true));
            extentX = 96;
            extentY = 96;
            for (int i = 0; i < seats.size(); i++) {
                var skill = seats.get(i);
                var line = CatalogIndex.line(player, skill.id());
                var branch = branches.get(i);
                String search = searchable(skill.name(), skill.id(), skill.description());
                int color = 0xff000000 | skill.color();
                List<AdaptationView> adaptations = children.get(i);
                String skillSearch = search + " " + adaptations.stream()
                        .map(a -> searchable(a.name(), a.id(), a.description())).collect(Collectors.joining(" "));
                next.add(new Node(new Route.Skill(skill.id()), branch.skill(), CENTER, LegacyText.strip(skill.name()),
                        skillSearch, skill.icon(), color, line == null ? 0 : line.level(), 0,
                        line == null ? 0 : line.progress(), player.access().canOpen(skill.id()), true));
                include(branch.skill());
                for (int j = 0; j < adaptations.size(); j++) {
                    var adaptation = adaptations.get(j);
                    var point = branch.adaptations().get(j);
                    include(point);
                    next.add(new Node(new Route.Adaptation(adaptation.id()), point, branch.skill(),
                            LegacyText.strip(adaptation.name()), search + " " + searchable(adaptation.name(), adaptation.id(), adaptation.description()),
                            adaptation.icon(), color, CatalogIndex.level(player, adaptation.id()), adaptation.maxLevel(),
                            0, true, false));
                }
            }
            nodes = List.copyOf(next);
            if (!manualView) fit(viewport);
        }

        private static String searchable(String name, String id, String description) {
            return LegacyText.strip(name + " " + id + " " + description).toLowerCase(Locale.ROOT);
        }

        private void include(SkillTreeLayout.Point point) {
            extentX = Math.max(extentX, Math.abs(point.x()));
            extentY = Math.max(extentY, Math.abs(point.y()));
        }

        private void fit(Viewport viewport) {
            camera.fit(viewport.width(), viewport.bottom() - viewport.top(), extentX, extentY);
            fittedViewport = viewport;
        }

        private void cancelGesture() {
            button = -1;
            dragging = false;
        }
    }
}

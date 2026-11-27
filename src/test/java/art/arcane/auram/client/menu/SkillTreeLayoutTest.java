package art.arcane.auram.client.menu;

import art.arcane.auram.client.menu.SkillTreeLayout.Anchor;
import art.arcane.auram.client.menu.SkillTreeLayout.Camera;
import art.arcane.auram.client.menu.SkillTreeLayout.Placement;
import art.arcane.auram.client.menu.SkillTreeLayout.Point;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SkillTreeLayoutTest {
    @Test
    public void denseBranchesSpreadWithoutMovingThePlayer() {
        Point center = new Point(0, 0);
        List<Anchor> anchors = new ArrayList<>();
        anchors.add(anchor(center, 30));
        for (var branch : SkillTreeLayout.branches(Collections.nCopies(20, 15))) {
            anchors.add(anchor(branch.skill(), 30));
            for (int i = 0; i < branch.adaptations().size(); i++) {
                anchors.add(anchor(branch.adaptations().get(i), i % 3 == 0 ? 26 : 19));
            }
        }
        List<Point> spread = SkillTreeLayout.spread(anchors);
        assertEquals(center, spread.get(0));
        for (int i = 0; i < spread.size(); i++) {
            for (int j = i + 1; j < spread.size(); j++) {
                assertTrue("Overlapping icons " + i + " and " + j,
                        spread.get(i).distance(spread.get(j)) >= anchors.get(i).radius() + anchors.get(j).radius() - 6);
            }
        }
    }

    @Test
    public void unlockedNodesReserveMoreSpaceAndMovementSettles() {
        var center = anchor(new Point(0, 0), 30);
        var neighbor = anchor(new Point(103, 0), 19);
        var unlearned = anchor(new Point(90, 0), 19);
        var learned = anchor(unlearned.seat(), 26);
        var normal = SkillTreeLayout.spread(List.of(center, unlearned, neighbor));
        var extra = SkillTreeLayout.spread(List.of(center, learned, neighbor));
        assertEquals(38, normal.get(1).distance(normal.get(2)), 1e-6);
        assertEquals(45, extra.get(1).distance(extra.get(2)), 1e-6);

        Placement moving = learned.placement();
        moving.moveTo(extra.get(1), 0);
        moving.advance(100_000_000);
        assertTrue(moving.point().x() < learned.seat().x() && moving.point().x() > extra.get(1).x());
        moving.advance(1_000_000_000);
        assertEquals(extra.get(1), moving.point());
    }

    @Test
    public void wheelZoomStaysAtTheCursorAndFinishesAtOneSecond() {
        Camera camera = new Camera();
        camera.fit(700, 700, 96, 96, 0);
        var worldPoint = new Point(40, -70);
        var cursor = camera.toScreen(worldPoint, 350, 350);
        camera.zoom(cursor.x(), cursor.y(), 350, 350, 1, 0);
        camera.advance(100_000_000);
        assertTrue(camera.scale() > 1 && camera.scale() < 1.18);
        assertEquals(0, cursor.distance(camera.toScreen(worldPoint, 350, 350)), 1e-9);
        camera.advance(999_000_000);
        assertTrue(camera.scale() < 1.18);
        camera.advance(1_000_000_000);
        assertEquals(1.18, camera.scale(), 0);
        assertEquals(0, cursor.distance(camera.toScreen(worldPoint, 350, 350)), 1e-9);
    }

    @Test
    public void changingZoomDirectionAndPanningDoNotJumpTheCamera() {
        Camera camera = new Camera();
        camera.fit(700, 700, 96, 96, 0);
        var point = new Point(-20, 30);
        camera.zoom(300, 300, 350, 350, 2, 0);
        camera.advance(125_000_000);
        var cursor = camera.toScreen(point, 350, 350);
        camera.zoom(cursor.x(), cursor.y(), 350, 350, -2, 125_000_000);
        assertEquals(cursor, camera.toScreen(point, 350, 350));
        camera.pan(16, -23);
        camera.advance(1_125_000_000);
        assertEquals(1, camera.scale(), 1e-9);
        assertEquals(0, new Point(cursor.x() + 16, cursor.y() - 23).distance(camera.toScreen(point, 350, 350)), 1e-9);
    }

    private static Anchor anchor(Point point, double radius) {
        return new Anchor(new Placement(point), point, radius);
    }
}

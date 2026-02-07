package art.arcane.auram.quests;

import net.minecraft.resources.ResourceLocation;
import java.util.*;

public class QuestLayout {
    static class Pos { double x, y; }

    public Map<ResourceLocation, Pos> positions = new HashMap<>();

    private Map<ResourceLocation, Double> logicalYMap = new HashMap<>();
    private Map<ResourceLocation, Integer> depthMap = new HashMap<>();
    private Map<Integer, Double> maxLogicalYPerDepth = new HashMap<>();
    private Set<ResourceLocation> recursionStack = new HashSet<>();

    private final double MAX_COLUMN_HEIGHT;
    private final double QUEST_WIDTH = 1.0;
    private final double HORIZONTAL_GAP;

    public QuestLayout(double horizontalGap, double maxYHeight) {
        this.HORIZONTAL_GAP = horizontalGap;
        this.MAX_COLUMN_HEIGHT = maxYHeight < 1 ? 12 : maxYHeight;
    }

    public void calculate(List<ResourceLocation> roots, Map<ResourceLocation, List<ResourceLocation>> tree) {
        for (ResourceLocation root : roots) {
            computeLogicalStructure(root, tree, 0);
        }

        Map<Integer, Double> depthStartXMap = new HashMap<>();

        int maxDepth = maxLogicalYPerDepth.keySet().stream().mapToInt(i -> i).max().orElse(0);
        double currentX = 0.0;

        for (int d = 0; d <= maxDepth; d++) {
            depthStartXMap.put(d, currentX);

            double count = maxLogicalYPerDepth.getOrDefault(d, 0.0);
            int cols = (int) Math.ceil(count / MAX_COLUMN_HEIGHT);
            if (cols == 0) cols = 1;

            double width = cols * QUEST_WIDTH;
            currentX -= (width + HORIZONTAL_GAP);
        }

        logicalYMap.forEach((id, logY) -> {
            int depth = depthMap.get(id);
            double startX = depthStartXMap.get(depth);

            int wrapCol = (int) (logY / MAX_COLUMN_HEIGHT);
            double visualY = logY % MAX_COLUMN_HEIGHT;

            double visualX = startX - (wrapCol * QUEST_WIDTH);

            Pos pos = new Pos();
            pos.x = visualX;
            pos.y = visualY;
            positions.put(id, pos);
        });
    }

    private void computeLogicalStructure(ResourceLocation current, Map<ResourceLocation, List<ResourceLocation>> tree, int depth) {
        if (logicalYMap.containsKey(current)) return;
        if (recursionStack.contains(current)) return;

        recursionStack.add(current);
        depthMap.put(current, depth);

        List<ResourceLocation> children = tree.getOrDefault(current, Collections.emptyList());
        if (!children.isEmpty()) {
            for (ResourceLocation child : children) {
                computeLogicalStructure(child, tree, depth + 1);
            }
        }

        double y = maxLogicalYPerDepth.getOrDefault(depth, 0.0);
        maxLogicalYPerDepth.put(depth, y + 1.0);
        logicalYMap.put(current, y);

        recursionStack.remove(current);
    }
}
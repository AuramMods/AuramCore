package art.arcane.auram.client.menu;

import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.LearnRules;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SkillTreeProgression {
    private SkillTreeProgression() {}

    static boolean discovered(PlayerView player, SkillView skill) {
        if (!skill.enabled() || !player.access().canOpen(skill.id())) return false;
        var line = CatalogIndex.line(player, skill.id());
        return line != null && (line.xp() > 0 || line.level() > 0 || line.knowledge() > 0)
                || skill.adaptations().stream().anyMatch(adaptation -> CatalogIndex.level(player, adaptation.id()) > 0);
    }

    public static List<List<AdaptationView>> reveal(PlayerView player, List<List<AdaptationView>> groups) {
        if (player == null || groups.isEmpty()) return List.of();
        Map<String, Long> earnedByLine = new HashMap<>();
        return groups.stream().map(group -> group.stream().filter(adaptation -> {
            if (!adaptation.enabled()) return false;
            if (CatalogIndex.level(player, adaptation.id()) > 0) return true;
            long earned = earnedByLine.computeIfAbsent(adaptation.skillId(), id -> levelKnowledge(player, id));
            return earned >= LearnRules.knowledgeCost(adaptation, 0, 1)
                    && LearnRules.missing(player, adaptation).isEmpty();
        }).toList()).filter(group -> !group.isEmpty()).toList();
    }

    private static long levelKnowledge(PlayerView player, String skillId) {
        var line = CatalogIndex.line(player, skillId);
        if (line == null) return 0;
        long earned = 0;
        // Adapt grants the award for the previous level each time a player levels up.
        for (int previous = 0; previous < line.level(); previous++) {
            earned += PlayerSkillLine.knowledgeForLevelUp(previous);
        }
        return earned;
    }
}

package art.arcane.auram.quests;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

public class QuestPool {
    public List<QChapterGroup> chapterGroups = new ArrayList<>();
    public List<QChapter> chapters = new ArrayList<>();
    public final List<QRewardTable> rewardTables = new ArrayList<>();
}

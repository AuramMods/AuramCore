package art.arcane.auram.quests;

import net.minecraft.nbt.CompoundTag;

public class QuestObject implements IQuestObject {
    public String id;
    
    public QuestObject() {
        this.id = rid();
    }
    
    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        return tag;
    }
}

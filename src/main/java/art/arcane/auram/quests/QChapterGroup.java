package art.arcane.auram.quests;

import net.minecraft.nbt.CompoundTag;

public class QChapterGroup extends QuestObject { 
    public String title;
    
    public QChapterGroup(String title) {
        super();
        this.title = title;
    }
    
    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = super.toNBT();
        
        tag.putString("title", title);
        
        return tag;
    }
}

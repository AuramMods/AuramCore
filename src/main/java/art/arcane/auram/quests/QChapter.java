package art.arcane.auram.quests;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

public class QChapter extends QuestObject {
    public String fileName;
    public String group;
    public List<String> subtitles;
    public String title;
    public Item icon;
    public int orderIndex;
    public final List<QQuest> quests;
    public Boolean consumeItems;
    public Boolean hideDependencyLinesByDefault;
    public String defaultQuestShape;
    public Boolean hideQuestDetailsUntilStartable;
    public Boolean hideQuestUntilDependenciesComplete;
    public Boolean hideQuestUntilDependenciesVisible;
    
    public QChapter(String fileName) {
        this.orderIndex = 0;
        this.fileName = fileName;
        this.quests = new ArrayList<>();
        this.title = null;
        this.subtitles = new ArrayList<>();
        this.icon = null;
        this.group = null;
        this.consumeItems = null;
        this.hideDependencyLinesByDefault = null;
        this.defaultQuestShape = "circle";
        this.hideQuestDetailsUntilStartable = null;
        this.hideQuestUntilDependenciesComplete = null;
        this.hideQuestUntilDependenciesVisible = null;
    }
    
    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = super.toNBT();
        tag.putString("filename", fileName);
        tag.putInt("order_index", orderIndex);
        
        if(consumeItems != null) {
            tag.putBoolean("consume_items", consumeItems);
        }
        
        if(hideDependencyLinesByDefault != null) {
            tag.putBoolean("default_hide_dependency_lines", hideDependencyLinesByDefault);
        }
        
        if(defaultQuestShape != null) {
            tag.putString("default_quest_shape", defaultQuestShape);
        }
        
        if(hideQuestDetailsUntilStartable != null) {
            tag.putBoolean("hide_quest_details_until_startable", hideQuestDetailsUntilStartable);
        }
        
        if(hideQuestUntilDependenciesComplete != null) {
            tag.putBoolean("hide_quest_until_deps_complete", hideQuestUntilDependenciesComplete);
        }
        
        if(hideQuestUntilDependenciesVisible != null) {
            tag.putBoolean("hide_quest_until_deps_visible", hideQuestUntilDependenciesVisible);
        }
        
        if(this.title != null) {
            tag.putString("title", this.title);
        }
        
        if(this.group != null) {
            tag.putString("group", this.group);
        }
        
        if(this.icon != null) {
            tag.putString("icon", sloc(this.icon));
        }
        
        if(this.subtitles != null && !this.subtitles.isEmpty()) {
            ListTag l = new ListTag();
            for(String s : this.subtitles) {
                l.add(StringTag.valueOf(s));
            }
            
            tag.put("subtitle", l);
        } 

        ListTag quests = new ListTag();
        
        for(QQuest i : this.quests) {
            quests.add(i.toNBT());
        }
        
        tag.put("quests", quests);
        
        return tag;
    }
}

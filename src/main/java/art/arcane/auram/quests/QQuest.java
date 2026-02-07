package art.arcane.auram.quests;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.ArrayList;
import java.util.List;

public class QQuest extends QuestObject {
    public String title;
    public String subtitle;
    public List<String> description;
    public List<QTask> tasks;
    public List<QReward> rewards;
    public List<String> dependencies;
    public double x;
    public double y;
    public Boolean canRepeat;
    public Boolean hideDependencyLines;
    public Boolean hideDetailsUntilStartable;
    public Boolean hideLockIcon;
    public Boolean hideTextUntilComplete;
    public Boolean hideUntilDepsComplete;
    public Boolean hideUntilDepsVisible;
    public Boolean ignoreRewardBlocking;
    public Boolean invisible;
    public Integer invisibleUntilTasks;
    public Boolean optional;
    public String shape;

    public QQuest(double x, double y) {
        super();
        this.title = null;
        this.subtitle = null;
        this.description = new ArrayList<>();
        this.tasks = new ArrayList<>();
        this.rewards = new ArrayList<>();
        this.dependencies = new ArrayList<>();
        this.x = x;
        this.y = y;
        this.canRepeat = null;
        this.hideDependencyLines = null;
        this.hideDetailsUntilStartable = null;
        this.hideLockIcon = null;
        this.hideTextUntilComplete = null;
        this.hideUntilDepsComplete = null;
        this.hideUntilDepsVisible = null;
        this.ignoreRewardBlocking = null;
        this.invisible = null;
        this.invisibleUntilTasks = null;
        this.optional = null;
        this.shape = null;
    }

    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = super.toNBT();
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        
        if(canRepeat != null) {
            tag.putBoolean("can_repeat", canRepeat);
        }
        
        if(shape != null) {
            tag.putString("shape", shape);
        }
        
        if(hideDependencyLines != null) {
            tag.putBoolean("hide_dependency_lines", hideDependencyLines);
        }
        
        if(hideDetailsUntilStartable != null) {
            tag.putBoolean("hide_details_until_startable", hideDetailsUntilStartable);
        }
        
        if(hideLockIcon != null) {
            tag.putBoolean("hide_lock_icon", hideLockIcon);
        }
        
        if(hideTextUntilComplete != null) {
            tag.putBoolean("hide_text_until_complete", hideTextUntilComplete);
        }
        
        if(hideUntilDepsComplete != null) {
            tag.putBoolean("hide_until_deps_complete", hideUntilDepsComplete);
        }
        
        if(hideUntilDepsVisible != null) {
            tag.putBoolean("hide_until_deps_visible", hideUntilDepsVisible);
        }
        
        if(ignoreRewardBlocking != null) {
            tag.putBoolean("ignore_reward_blocking", ignoreRewardBlocking);
        }
        
        if(invisible != null) {
            tag.putBoolean("invisible", invisible);
        }
        
        if(invisibleUntilTasks != null) {
            tag.putInt("invisible_until_tasks", invisibleUntilTasks);
        }
        
        if(optional != null) {
            tag.putBoolean("optional", optional);
        }
        
        if(this.title != null) {
            tag.putString("title", this.title);
        }
        
        if(this.subtitle != null) {
            tag.putString("subtitle", this.subtitle);
        }
        
        if(this.description != null && !this.description.isEmpty()) {
            ListTag l = new ListTag();
            for(String s : this.description) {
                l.add(StringTag.valueOf(s));
            }
            
            tag.put("description", l);
        }
        
        if(this.dependencies != null && !this.dependencies.isEmpty()) {
            ListTag l = new ListTag();
            for(String s : this.dependencies) {
                l.add(StringTag.valueOf(s));
            }
            
            tag.put("dependencies", l);
        }
        
        if(this.tasks != null && !this.tasks.isEmpty()) {
            ListTag l = new ListTag();
            for(QTask t : this.tasks) {
                l.add(t.toNBT());
            }
            
            tag.put("tasks", l);
        }
        
        if(this.rewards != null && !this.rewards.isEmpty()) {
            ListTag l = new ListTag();
            for(QReward r : this.rewards) {
                l.add(r.toNBT());
            }
            
            tag.put("rewards", l);
        }
        
        return tag;
    }
}

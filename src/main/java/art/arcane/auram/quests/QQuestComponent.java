package art.arcane.auram.quests;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;

public class QQuestComponent extends QuestObject {
    public String type;
    public String title;
    public Item icon;

    public QQuestComponent(String type) {
        super();
        this.type = type;
        this.title = null;
        this.icon = null;
    }

    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = super.toNBT();
        tag.putString("type", type);

        if(title != null) {
            tag.putString("title", title);
        }

        if(icon != null) {
            tag.putString("icon", sloc(icon));
        }

        return tag;
    }
}

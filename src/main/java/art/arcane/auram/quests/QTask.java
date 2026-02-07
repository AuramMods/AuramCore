package art.arcane.auram.quests;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;

public class QTask extends QQuestComponent {
    public QTask(String type) {
        super(type);
    }

    @Override
    public CompoundTag toNBT() {
        return super.toNBT();
    }
}

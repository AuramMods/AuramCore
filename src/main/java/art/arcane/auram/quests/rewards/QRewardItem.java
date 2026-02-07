package art.arcane.auram.quests.rewards;

import art.arcane.auram.quests.QReward;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class QRewardItem extends QReward {
    final ItemStack item;
    
    public QRewardItem(ItemStack item) {
        super("item");
        this.item = item;
    }
    
    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = super.toNBT();
        tag.putString("type", "item");
        tag.put("item",  item.serializeNBT());

        return tag;
    }
}

package art.arcane.auram.quests.tasks;

import art.arcane.auram.quests.QTask;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class QTaskItem extends QTask {
    final ItemStack item;
    final boolean consume;
    
    public QTaskItem(ItemStack item, boolean consume) {
        super("item");
        this.item = item;
        this.consume = consume;
    }
    
    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = super.toNBT();
        tag.putBoolean("consume_items", consume);
        tag.putString("item", sloc(item));
        
        if(item.getCount() > 1) {
            tag.putInt("count", item.getCount());
        }
        
        return tag;
    }
}

package art.arcane.auram.quests.rewards;

import art.arcane.auram.quests.QReward;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

public class QRewardXP extends QReward {
    public int xp;
    
    public QRewardXP( int xp) {
        super("xp");
        this.xp = xp;
    }   
    
    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = super.toNBT();
        tag.putInt("xp", xp);
        return tag;
    }
}

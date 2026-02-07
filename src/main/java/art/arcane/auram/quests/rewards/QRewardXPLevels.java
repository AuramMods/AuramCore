package art.arcane.auram.quests.rewards;

import art.arcane.auram.quests.QReward;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

public class QRewardXPLevels extends QReward {
    public int xpLevels;
    
    public QRewardXPLevels(int xpLevels) {
        super("xp_levels");
        this.xpLevels = xpLevels;
    }   
    
    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = super.toNBT();
        tag.putInt("xp_levels", xpLevels);
        return tag;
    }
}

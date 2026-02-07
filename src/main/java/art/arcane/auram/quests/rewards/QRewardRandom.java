package art.arcane.auram.quests.rewards;

import art.arcane.auram.quests.QReward;
import net.minecraft.nbt.CompoundTag;

public class QRewardRandom extends QReward {
    public String tableId;
    public Boolean excludeFromClaimAll;
    
    public QRewardRandom(String tableId) {
        super("random");
        this.tableId = tableId;
        this.excludeFromClaimAll = true;
    }
    
    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = super.toNBT();
        if(tableId != null) {
            long hexTo = Long.parseLong(tableId.replace("-", ""), 16);
            tag.putLong("table_id", hexTo);
        }
        
        if(excludeFromClaimAll != null) {
            tag.putBoolean("exclude_from_claim_all", excludeFromClaimAll);
        }
        return tag;
    }
}

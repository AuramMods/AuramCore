package art.arcane.auram.quests;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QRewardTable extends QuestObject {
    public Float emptyWeight;
    public Item icon;
    public Integer crateColor;
    public Integer crateDropsBoss;
    public Integer crateDropsMonster;
    public Integer crateDropsPassive;
    public Byte crateGlow;
    public String crateName;
    public String crateId;
    public Integer lootSize;
    public Integer orderIndex;
    public Map<Item, Float> rewards;
    public Map<ItemStack, Float> nbtRewards;
    public String title;
    
    public QRewardTable() {
        super();
        this.emptyWeight = null;
        this.icon = null;
        this.crateColor = null;
        this.crateDropsBoss = null;
        this.crateDropsMonster = null;
        this.crateDropsPassive = null;
        this.crateGlow = null;
        this.crateName = null;
        this.crateId = null;
        this.lootSize = null;
        this.orderIndex = 0;
        this.rewards = new HashMap<>();
        this.nbtRewards = new HashMap<>();
        this.title = null;
    }
 
    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = super.toNBT();
        tag.putString("id", id);
        
        if(emptyWeight != null) {
            tag.putFloat("empty_weight", emptyWeight);
        }

        if(icon != null) {
            tag.putString("icon", sloc(icon));
        }
        
        if(crateColor != null || crateDropsBoss != null || crateDropsMonster != null || crateDropsPassive != null || crateGlow != null || crateName != null || crateId != null) {
            CompoundTag crateTag = new CompoundTag();
            
            if(crateColor != null) {
                crateTag.putInt("color", crateColor);
            }
            
            if(crateDropsBoss != null || crateDropsMonster != null || crateDropsPassive != null) {
                CompoundTag dropsTag = new CompoundTag();
                
                if(crateDropsBoss != null) {
                    dropsTag.putInt("boss", crateDropsBoss);
                }
                
                if(crateDropsMonster != null) {
                    dropsTag.putInt("monster", crateDropsMonster);
                }
                
                if(crateDropsPassive != null) {
                    dropsTag.putInt("passive", crateDropsPassive);
                }
                
                crateTag.put("drops", dropsTag);
            }
            
            if(crateGlow != null) {
                crateTag.putByte("glow", crateGlow);
            }
            
            if(crateName != null) {
                crateTag.putString("item_name", crateName);
            }
            
            if(crateId != null) {
                crateTag.putString("string_id", crateId);
            }
            
            tag.put("loot_crate", crateTag);
        }
        
        if(lootSize != null) {
            tag.putInt("loot_size", lootSize);
        }
        
        if(orderIndex != null) {
            tag.putInt("order_index", orderIndex);
        }

        ListTag listTag = new ListTag();
        if(rewards != null && !rewards.isEmpty()) {
            List<CompoundTag> rewardTags = rewards.entrySet().stream().map(entry -> {
                CompoundTag rewardTag = new CompoundTag();
                ItemStack stack = new ItemStack(entry.getKey());
                rewardTag.putString("item", sloc(stack.getItem()));
                if(entry.getValue() != null) {
                    rewardTag.putFloat("weight", entry.getValue());
                }
                return rewardTag;
            }).toList();

            listTag.addAll(rewardTags);
        }
        
        if(nbtRewards != null && !nbtRewards.isEmpty()) {
            List<CompoundTag> nbtRewardTags = nbtRewards.entrySet().stream().map(entry -> {
                CompoundTag rewardTag = new CompoundTag();
                ItemStack stack = entry.getKey();
                CompoundTag ct = stack.serializeNBT();
                ct.putInt("Count", 1);
                rewardTag.put("item", ct);
                if(entry.getValue() != null) {
                    rewardTag.putFloat("weight", entry.getValue());
                }
                return rewardTag;
            }).toList();

            listTag.addAll(nbtRewardTags);
        }
        
        if(!listTag.isEmpty()) {
            tag.put("rewards", listTag);
        }
        
        if(title != null) {
            tag.putString("title", title);
        }
        
        
        return tag;
    }
}

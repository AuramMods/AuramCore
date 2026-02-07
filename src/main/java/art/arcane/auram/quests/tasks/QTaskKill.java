package art.arcane.auram.quests.tasks;

import art.arcane.auram.quests.QTask;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class QTaskKill extends QTask {
    public ResourceLocation entity;
    public long value;
    
    public QTaskKill(ResourceLocation entity, long value) {
        super("kill");
        this.entity = entity;
        this.value = value;
    }
    
    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = super.toNBT();
        
        if(entity != null) {
            tag.putString("entity", entity.toString());
        }
        
        tag.putLong("value", value);
        
        return tag;
    }
}

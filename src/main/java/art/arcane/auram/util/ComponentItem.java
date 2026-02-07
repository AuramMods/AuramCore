package art.arcane.auram.util;

import art.arcane.auram.recipes.VirtualRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public interface ComponentItem {
    String NBT_CUSTOM_NAME = "ComponentName";
    String NBT_MATERIAL_KEY = "ComponentMaterial";
    
    String getRawName();
    
    default Item getRawItem() {
        return (Item) this;
    }
    
    default ItemStack transform(ItemStack base){
        return transform(base.getHoverName().getString(), BuiltInRegistries.ITEM.getKey(base.getItem()));
    }
    
    ItemStack transform(String name, ResourceLocation materialKey);
    
    VirtualRecipe getRecipe(ItemStack comp);
}

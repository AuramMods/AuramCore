package art.arcane.auram.util;

import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

public class EMCHelper {
    public static long getEmcValue(Ingredient ingredient) {
        if (ingredient.isEmpty()) return 0;
        ItemStack[] matchingStacks = ingredient.getItems();
        if (matchingStacks.length == 0) return 0;
        long totalEmc = 0;
        int validCount = 0;

        for (ItemStack stack : matchingStacks) {
            long value = getEmcValue(stack);
            
            if (value > 0) {
                totalEmc += value;
                validCount++;
            }
        }
 
        return validCount == 0 ? 0 : totalEmc / validCount;
    }
    
    public static long getEmcValue(ItemStack stack) {
        IEMCProxy proxy = IEMCProxy.INSTANCE;

        if (proxy.hasValue(stack)) {
            return proxy.getValue(stack);
        }
        
        return 0; 
    }
    
    public static long getEmcValue(Item item) {
        IEMCProxy proxy = IEMCProxy.INSTANCE;
        if (proxy.hasValue(item)) {
            return proxy.getValue(item);
        }
        return 0;
    }
}
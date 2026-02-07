package art.arcane.auram.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;
 
public class ChiselEnchantment extends Enchantment {
    public ChiselEnchantment() {
        super(Rarity.UNCOMMON, EnchantmentCategory.DIGGER, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int level) {
        return 5 + (5 * (level - 1));
    }

    @Override
    public int getMaxCost(int level) {
        return super.getMinCost(level) + 25;
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }
    
    @Override
    public boolean checkCompatibility(Enchantment other) {
        if(other == Enchantments.SILK_TOUCH) {
            return false;
        }
        
        return super.checkCompatibility(other);
    }
}
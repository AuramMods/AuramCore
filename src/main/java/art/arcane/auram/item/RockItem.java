package art.arcane.auram.item;

import art.arcane.auram.Auram;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

public class RockItem extends Item {
    public RockItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Block originalBlock = Auram.ROCK_ITEM_TO_ORE_BLOCK.get(this);

        if (originalBlock != null) {
            String blockName = originalBlock.getName().getString();
            String rockName = blockName.replaceAll("(?i)\\bOre\\b", "Rock").trim();
            
            if (!rockName.toLowerCase().contains("rock")) {
                rockName += " Rock";
            }

            return Component.literal(rockName);
        }

        String path = ForgeRegistries.ITEMS.getKey(this).getPath();
        return Component.literal(org.apache.commons.lang3.text.WordUtils.capitalize(path.replace('_', ' ')));
    }
}
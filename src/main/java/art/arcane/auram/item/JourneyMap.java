package art.arcane.auram.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class JourneyMap extends Item {
    public JourneyMap() {
        super(new Properties());
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Journey Map");
    }
}

package art.arcane.auram.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class EnderJade extends Item {
    public EnderJade() {
        super(new Properties());
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Ender Jade");
    }
}

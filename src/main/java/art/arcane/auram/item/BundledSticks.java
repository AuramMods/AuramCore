package art.arcane.auram.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class BundledSticks extends Item {
    public BundledSticks() {
        super(new Item.Properties());
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Bundled Sticks");
    }
}

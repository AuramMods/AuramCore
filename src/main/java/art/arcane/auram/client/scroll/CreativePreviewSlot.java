package art.arcane.auram.client.scroll;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

public final class CreativePreviewSlot extends Slot {
    private final Supplier<ItemStack> item;

    public CreativePreviewSlot(Container container, int column, Supplier<ItemStack> item) {
        super(container, column, 9 + column * 18, 108);
        this.item = item;
        index = 36 + column;
    }

    @Override
    public ItemStack getItem() {
        return item.get();
    }

    @Override
    public boolean mayPickup(Player player) {
        ItemStack stack = getItem();
        return stack.isEmpty() || stack.isItemEnabled(player.level().enabledFeatures())
                && stack.getTagElement("CustomCreativeLock") == null;
    }
}

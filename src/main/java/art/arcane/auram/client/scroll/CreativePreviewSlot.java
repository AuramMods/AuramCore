package art.arcane.auram.client.scroll;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

import static art.arcane.auram.client.scroll.CreativeScrollViewport.GRID_BOTTOM;
import static art.arcane.auram.client.scroll.CreativeScrollViewport.GRID_LEFT;
import static art.arcane.auram.client.scroll.CreativeScrollViewport.SLOT_SIZE;

public final class CreativePreviewSlot extends Slot {
    private final Supplier<ItemStack> item;

    public CreativePreviewSlot(Container container, int column, Supplier<ItemStack> item) {
        super(container, column, GRID_LEFT + 1 + column * SLOT_SIZE, GRID_BOTTOM + 1);
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

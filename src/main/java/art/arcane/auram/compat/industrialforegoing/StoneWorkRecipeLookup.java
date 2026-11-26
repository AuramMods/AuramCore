package art.arcane.auram.compat.industrialforegoing;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Shares failed StoneWork crafting lookups within one client recipe registration. */
public final class StoneWorkRecipeLookup {
    private static final ThreadLocal<Map<Query, ItemStack>> MISSES = new ThreadLocal<>();

    private StoneWorkRecipeLookup() {}

    public static void duringRegistration(Runnable register) {
        Map<Query, ItemStack> previous = MISSES.get();
        MISSES.set(new HashMap<>());
        try {
            register.run();
        } finally {
            if (previous == null) MISSES.remove();
            else MISSES.set(previous);
        }
    }

    public static Optional<? extends Recipe<?>> find(RecipeManager recipes, Container grid, Level level,
                                                      Supplier<Optional<? extends Recipe<?>>> original) {
        var misses = MISSES.get();
        if (misses == null) return original.get();

        // CraftingUtils.findOutput fills every slot with a copy of this stack.
        ItemStack input = grid.getItem(0);
        var query = new Query(recipes, level, grid.getContainerSize(), input.getItem(), input.getCount());
        ItemStack previous = misses.get(query);
        if (previous != null && ItemStack.matches(input, previous)) {
            return Optional.empty();
        }

        Optional<? extends Recipe<?>> result = original.get();
        if (result.isEmpty() && previous == null) misses.put(query, input.copy());
        return result;
    }

    private record Query(RecipeManager recipes, Level level, int slots, Item item, int count) {}
}

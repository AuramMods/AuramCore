package art.arcane.auram.recipes;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VirtualRecipe {
    public final ResourceLocation id;
    public final String type;
    public final List<Ingredient> inputs;
    public final ItemStack output;
    public final Map<String, Object> properties = new HashMap<>();
    public final transient Recipe<?> originalRecipe;
    public boolean isModified = false;
    public boolean isCreated = false;

    public VirtualRecipe(ResourceLocation id, String type, List<Ingredient> inputs, ItemStack output, Recipe<?> original) {
        this.id = id;
        this.type = type;
        this.inputs = new ArrayList<>(inputs);
        this.output = output.copy();
        this.originalRecipe = original;
    }
}
package art.arcane.auram.recipes;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.function.Function;

abstract public class RecipeHandler {
    public final String recipeType;
    public final RecipeWorkspace workspace;
    
    public RecipeHandler(RecipeWorkspace workspace, String recipeType) {
        this.workspace = workspace;
        this.recipeType = recipeType;
    }

    public boolean isSupported(Recipe<?> recipe) {
        return recipe.getType().toString().equals(this.recipeType) && supported(recipe);
    }
    
    public abstract boolean supported(Recipe<?> recipe);
    
    public abstract String write(VirtualRecipe recipe, Function<Ingredient, String> formatter);
}

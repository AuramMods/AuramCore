package art.arcane.auram.recipes;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.storage.CommandStorage;

import java.util.Random;

public class ChaosContext {
    public final RecipeWorkspace workspace;
    public final Random random;
    public final int difficulty;
    public final CommandSourceStack source;
    public VirtualRecipe recipe;
    
    public ChaosContext(RecipeWorkspace workspace,CommandSourceStack source, Random random, int difficulty) {
        this.workspace = workspace;
        this.random = random;
        this.difficulty = difficulty;
        this.source = source;
    }

    public double diffScale() {
        return difficulty / 100.0;
    }
    
    public void swapIngredient(int index, Ingredient newIngredient) {
        recipe.inputs.set(index, newIngredient);
        recipe.isModified = true;
    }

    public double lerp(double a, double b, double f) {
        return a + f * (b - a);
    }
    
    public double inverseDiffScale() {
        return 1.0 - diffScale();
    }
}

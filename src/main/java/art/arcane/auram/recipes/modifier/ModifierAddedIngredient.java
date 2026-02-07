package art.arcane.auram.recipes.modifier;

import art.arcane.auram.recipes.ChaosContext;
import art.arcane.auram.recipes.IngredientFilter;
import art.arcane.auram.recipes.RecipeModifier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.Set;

public class ModifierAddedIngredient extends RecipeModifier {
    private IngredientFilter filter;
    
    public ModifierAddedIngredient() {
        super("Added Ingredient", 25);
    }

    @Override
    public void init(ChaosContext context) {
        filter = new IngredientFilter()
                .percentLower(1 + (context.diffScale() * 1.05))
                .percentHigher(1.1 + context.diffScale() * 2.25);
    }

    @Override
    public boolean evaluate(ChaosContext context) {
        return context.recipe.originalRecipe instanceof ShapelessRecipe || context.recipe.originalRecipe instanceof ShapedRecipe
                && context.recipe.inputs.size() < 9
                && context.random.nextDouble() < context.diffScale();
    }
    
    @Override
    public int apply(ChaosContext context) {
        int mods = 0;

        while(context.recipe.inputs.size() < 9 && context.random.nextDouble() < context.diffScale()) {
            Ingredient ri = context.recipe.inputs.get(context.random.nextInt(context.recipe.inputs.size()));
            
            if(context.random.nextDouble()*1.2 < context.diffScale()) {
                boolean tagged = context.workspace.isTagIngredient(ri);
                Set<Ingredient> candidates = context.workspace.getAlternatives(context.recipe, ri, filter.tagged(tagged));

                if(candidates.size() > 1) {
                    ri = candidates.stream().skip(context.random.nextInt(candidates.size())).findFirst().orElse(ri);
                }
            } else {
                break;
            }
            
            context.recipe.inputs.add(ri);
            context.recipe.isModified = true;
            mods++;
        }

        return mods;
    }
}

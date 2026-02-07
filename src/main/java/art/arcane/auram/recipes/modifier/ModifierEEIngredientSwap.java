package art.arcane.auram.recipes.modifier;

import art.arcane.auram.recipes.ChaosContext;
import art.arcane.auram.recipes.IngredientFilter;
import art.arcane.auram.recipes.RecipeModifier;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Set;

public class ModifierEEIngredientSwap extends RecipeModifier {
    private IngredientFilter filter;
    
    public ModifierEEIngredientSwap() {
        super("Equivalent Ingredient Swap", 75);
    }

    @Override
    public void init(ChaosContext context) {
        filter = new IngredientFilter()
                .percentLower(0.8 + (context.diffScale() * 0.75))
                .percentHigher(1.2 + context.diffScale() * 1.25);
    }

    @Override
    public boolean evaluate(ChaosContext context) {
        return context.random.nextDouble() < context.diffScale() * 0.75;
    }

    @Override
    public int apply(ChaosContext context) {
        int mods = 0;
        for(int i = 0; i < context.recipe.inputs.size(); i++) {
            if(context.random.nextDouble() < context.diffScale() * 0.5) {
                Ingredient ingredient = context.recipe.inputs.get(i);

                if(ingredient.getItems().length > 1) {
                    continue;
                }

                boolean tagged = context.workspace.isTagIngredient(ingredient);
                Set<Ingredient> candidates = context.workspace.getAlternatives(context.recipe, ingredient, filter.tagged(tagged));

                if(candidates.size() <= 1) {
                    continue;
                }

                context.swapIngredient(i, candidates.stream().skip(context.random.nextInt(candidates.size())).findFirst().orElse(ingredient));
                mods++;
            } else {
                break;
            }
        }

        return mods;
    }
}

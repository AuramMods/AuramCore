package art.arcane.auram.recipes.modifier;

import art.arcane.auram.Auram;
import art.arcane.auram.recipes.ChaosContext;
import art.arcane.auram.recipes.RecipeModifier;
import art.arcane.auram.recipes.VirtualRecipe;
import art.arcane.auram.util.ComponentItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public class ModifierComponent extends RecipeModifier {

    public ModifierComponent() {
        super("Component", 15);
    }

    @Override
    public void init(ChaosContext context) {

    }

    @Override
    public boolean evaluate(ChaosContext context) {
        return context.random.nextDouble() < context.diffScale();
    }
    
    @Override
    public int apply(ChaosContext context) {
        int mods = 0;

        for(int ind = 0; ind <   context.recipe.inputs.size(); ind++) {
            Ingredient i = context.recipe.inputs.get(ind);
            
            if(context.workspace.isTagIngredient(i) || i.getItems().length != 1){
                continue;
            }
            
            if(context.random.nextDouble() > context.diffScale()) {
                break;
            }

            ItemStack base = i.getItems()[0];
            
            if(base.getItem() instanceof ComponentItem) {
                continue;
            }
            
            ComponentItem componentItem = (ComponentItem) Auram.COMPONENTS[context.random.nextInt(Auram.COMPONENTS.length)].get();
            ItemStack compStack = componentItem.transform(base);
            VirtualRecipe compRecipe = componentItem.getRecipe(compStack);
            
            if(compRecipe == null) {
                continue;
            }
            
            if(!context.workspace.recipeMap.containsKey(compRecipe.id) && !context.workspace.createdRecipes.containsKey(compRecipe.id)) {
                synchronized (context.workspace.createdRecipes) {
                    context.workspace.createdRecipes.put(compRecipe.id, compRecipe);
                    mods++;
                }
            }
            
            context.swapIngredient(ind, Ingredient.of(compStack));
            mods++;
        }

        return mods;
    }
}

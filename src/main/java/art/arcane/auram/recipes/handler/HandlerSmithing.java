package art.arcane.auram.recipes.handler;

import art.arcane.auram.recipes.RecipeHandler;
import art.arcane.auram.recipes.RecipeWorkspace;
import art.arcane.auram.recipes.VirtualRecipe;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.*;

import java.util.function.Function;

public class HandlerSmithing extends RecipeHandler {
    public HandlerSmithing(RecipeWorkspace workspace) {
        super(workspace, RecipeType.SMITHING.toString());
    }

    @Override
    public boolean supported(Recipe<?> recipe) {
        return recipe instanceof SmithingRecipe;
    }

    @Override
    public String write(VirtualRecipe recipe, Function<Ingredient, String> formatter) {
        JsonObject json = new JsonObject();
        json.addProperty("type", recipe.type);
        
        
        if (recipe.inputs.size() >= 3) {
            json.add("template", RecipeWorkspace.serializeIngredient(recipe.inputs.get(0)));
            json.add("base", RecipeWorkspace.serializeIngredient(recipe.inputs.get(1)));
            json.add("addition", RecipeWorkspace.serializeIngredient(recipe.inputs.get(2)));
        }

        JsonObject result = new JsonObject();
        result.addProperty("item", BuiltInRegistries.ITEM.getKey(recipe.output.getItem()).toString());
        if (recipe.output.getCount() > 1) {
            result.addProperty("count", recipe.output.getCount());
        }
        json.add("result", result);

        return RecipeWorkspace.GSON.toJson(json);
    }
}
package art.arcane.auram.recipes.handler;

import art.arcane.auram.recipes.RecipeHandler;
import art.arcane.auram.recipes.RecipeWorkspace;
import art.arcane.auram.recipes.VirtualRecipe;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.*;

import java.util.function.Function;

public class HandlerStonecutting extends RecipeHandler {
    public HandlerStonecutting(RecipeWorkspace workspace) {
        super(workspace, RecipeType.STONECUTTING.toString());
    }

    @Override
    public boolean supported(Recipe<?> recipe) {
        return recipe instanceof StonecutterRecipe;
    }

    @Override
    public String write(VirtualRecipe recipe, Function<Ingredient, String> formatter) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:stonecutting");

        if (!recipe.inputs.isEmpty()) {
            json.add("ingredient", RecipeWorkspace.serializeIngredient(recipe.inputs.get(0)));
        }

        json.addProperty("result", BuiltInRegistries.ITEM.getKey(recipe.output.getItem()).toString());
        json.addProperty("count", recipe.output.getCount());

        return RecipeWorkspace.GSON.toJson(json);
    }
}
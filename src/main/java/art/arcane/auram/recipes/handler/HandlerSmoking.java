package art.arcane.auram.recipes.handler;

import art.arcane.auram.recipes.RecipeHandler;
import art.arcane.auram.recipes.RecipeWorkspace;
import art.arcane.auram.recipes.VirtualRecipe;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.*;

import java.util.function.Function;

public class HandlerSmoking extends RecipeHandler {
    public HandlerSmoking(RecipeWorkspace workspace) {
        super(workspace, RecipeType.SMOKING.toString());
    }

    @Override
    public boolean supported(Recipe<?> recipe) {
        return recipe instanceof SmokingRecipe;
    }

    @Override
    public String write(VirtualRecipe recipe, Function<Ingredient, String> formatter) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:smoking");

        if (!recipe.inputs.isEmpty()) {
            json.add("ingredient", RecipeWorkspace.serializeIngredient(recipe.inputs.get(0)));
        }
        
        SmokingRecipe original = (SmokingRecipe) recipe.originalRecipe;
        json.addProperty("result", BuiltInRegistries.ITEM.getKey(recipe.output.getItem()).toString());
        json.addProperty("experience", original.getExperience());
        json.addProperty("cookingtime", original.getCookingTime());

        return RecipeWorkspace.GSON.toJson(json);
    }
}
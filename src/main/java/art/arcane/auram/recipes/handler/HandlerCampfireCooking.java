package art.arcane.auram.recipes.handler;

import art.arcane.auram.recipes.RecipeHandler;
import art.arcane.auram.recipes.RecipeWorkspace;
import art.arcane.auram.recipes.VirtualRecipe;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.*;

import java.util.function.Function;

public class HandlerCampfireCooking extends RecipeHandler {
    public HandlerCampfireCooking(RecipeWorkspace workspace) {
        super(workspace, RecipeType.CAMPFIRE_COOKING.toString());
    }

    @Override
    public boolean supported(Recipe<?> recipe) {
        return recipe instanceof CampfireCookingRecipe;
    }

    @Override
    public String write(VirtualRecipe recipe, Function<Ingredient, String> formatter) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:campfire_cooking");

        if (!recipe.inputs.isEmpty()) {
            json.add("ingredient", RecipeWorkspace.serializeIngredient(recipe.inputs.get(0)));
        }

        json.addProperty("result", BuiltInRegistries.ITEM.getKey(recipe.output.getItem()).toString());

        if (recipe.originalRecipe instanceof CampfireCookingRecipe original) {
            json.addProperty("experience", original.getExperience());
            json.addProperty("cookingtime", original.getCookingTime());
        } else {
            json.addProperty("experience", 0.35);
            json.addProperty("cookingtime", 100);
        }
        
        

        return RecipeWorkspace.GSON.toJson(json);
    }
}
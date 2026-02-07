package art.arcane.auram.recipes.handler;

import art.arcane.auram.recipes.RecipeHandler;
import art.arcane.auram.recipes.RecipeWorkspace;
import art.arcane.auram.recipes.VirtualRecipe;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.function.Function;

public class HandlerCraftingShapeless extends RecipeHandler {
    public HandlerCraftingShapeless(RecipeWorkspace workspace) {
        super(workspace, RecipeType.CRAFTING.toString());
    } 

    @Override
    public boolean supported(Recipe<?> recipe) {
        return recipe instanceof ShapelessRecipe;
    }

    @Override
    public String write(VirtualRecipe recipe, Function<Ingredient, String> formatter) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shapeless");
        JsonArray ingredients = new JsonArray();
        for (Ingredient input : recipe.inputs) {
            if (!input.isEmpty()) {
                ingredients.add(RecipeWorkspace.serializeIngredient(input));
            }
        }
        json.add("ingredients", ingredients);
        JsonObject result = new JsonObject();
        result.addProperty("item", BuiltInRegistries.ITEM.getKey(recipe.output.getItem()).toString());
        if (recipe.output.getCount() > 1) {
            result.addProperty("count", recipe.output.getCount());
        }
        if (recipe.output.hasTag()) {
            result.addProperty("nbt", recipe.output.getTag().toString());
        }
        json.add("result", result);

        return RecipeWorkspace.GSON.toJson(json);
    }
}

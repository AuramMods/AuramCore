package art.arcane.auram.recipes.handler;

import art.arcane.auram.recipes.RecipeHandler;
import art.arcane.auram.recipes.RecipeWorkspace;
import art.arcane.auram.recipes.VirtualRecipe;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class HandlerCraftingShaped extends RecipeHandler {
    public HandlerCraftingShaped(RecipeWorkspace workspace) {
        super(workspace, RecipeType.CRAFTING.toString());
    }

    @Override
    public boolean supported(Recipe<?> recipe) {
        return recipe instanceof ShapedRecipe;
    }

    @Override
    public String write(VirtualRecipe recipe, Function<Ingredient, String> formatter) {
        ShapedRecipe original = (ShapedRecipe) recipe.originalRecipe;
        int width = original.getWidth();
        int height = original.getHeight();

        // 1. AUTO-EXPAND DIMENSIONS
        // If the VirtualRecipe inputs have grown larger than the original grid 
        // (e.g. Chaos added items to a previously small recipe), force 3x3.
        while (recipe.inputs.size() > width * height) {
            if(width+1 <= 3){
                width++;
            } else if(height+1 <=3){
                height++;
            }
            
            if(height>=3 || width>=3) {
                width= 3;
                height = 3;
                break;
            }
        }

        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shaped");

        JsonArray pattern = new JsonArray();
        JsonObject key = new JsonObject();

        // Map unique Ingredient JSON strings to a Character (e.g., "minecraft:dirt" -> 'A')
        Map<String, Character> ingredientToChar = new HashMap<>();
        char currentChar = 'A';

        for (int y = 0; y < height; y++) {
            StringBuilder rowBuilder = new StringBuilder();
            for (int x = 0; x < width; x++) {
                // Calculate index for flat list
                int index = x + (y * width);

                // Safety check: if grid implies a slot that doesn't exist in list, call it empty
                if (index >= recipe.inputs.size()) {
                    rowBuilder.append(" ");
                    continue;
                }

                Ingredient ing = recipe.inputs.get(index);

                if (ing.isEmpty()) {
                    rowBuilder.append(" ");
                } else {
                    JsonElement ingJson = RecipeWorkspace.serializeIngredient(ing);
                    String ingJsonString = ingJson.toString();
                    
                    if (!ingredientToChar.containsKey(ingJsonString)) {
                        ingredientToChar.put(ingJsonString, currentChar);
                        key.add(String.valueOf(currentChar), ingJson);
                        currentChar++;
                    }

                    // Append the assigned Char to the pattern
                    rowBuilder.append(ingredientToChar.get(ingJsonString));
                }
            }
            pattern.add(rowBuilder.toString());
        }

        json.add("pattern", pattern);
        json.add("key", key);

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
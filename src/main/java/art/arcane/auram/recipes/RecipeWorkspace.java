package art.arcane.auram.recipes;

import art.arcane.auram.Auram;
import art.arcane.auram.recipes.handler.*;
import art.arcane.auram.recipes.modifier.ModifierAddedIngredient;
import art.arcane.auram.recipes.modifier.ModifierComponent;
import art.arcane.auram.recipes.modifier.ModifierEEIngredientSwap;
import art.arcane.auram.util.ComponentItem;
import art.arcane.auram.util.EMCHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jdk.jfr.consumer.RecordedObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecipeWorkspace {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public final Map<ResourceLocation, VirtualRecipe> recipeMap = new LinkedHashMap<>();
    public final Map<ResourceLocation, VirtualRecipe> createdRecipes = new LinkedHashMap<>();
    public final Set<Ingredient> uniqueIngredients = new HashSet<>();
    public final Map<Ingredient, Long> ingredientEmcCache = new HashMap<>();
    private final Map<Item, List<VirtualRecipe>> recipesByOutput = new HashMap<>();
    public final Set<RecipeType<?>> recipeTypes = new HashSet<>();
    public final List<RecipeHandler> handlers = new ArrayList<>();
    public final List<RecipeModifier> modifiers = new ArrayList<>();

    public void addHandlers(){
        handlers.clear();
        handlers.add(new HandlerCraftingShapeless(this));
        handlers.add(new HandlerCraftingShaped(this));
        handlers.add(new HandlerSmelting(this));
        handlers.add(new HandlerBlasting(this));
        handlers.add(new HandlerSmoking(this));
        handlers.add(new HandlerCampfireCooking(this));
        handlers.add(new HandlerStonecutting(this));
        handlers.add(new HandlerSmithing(this));
    }

    public void addModifiers(){
        modifiers.clear();
        modifiers.add(new ModifierEEIngredientSwap());
        modifiers.add(new ModifierComponent());
        modifiers.add(new ModifierAddedIngredient());
    } 

    public void loadFromGameParallel(Level level) {
        addHandlers();
        addModifiers();
        recipeMap.clear();

        ExecutorService executor = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());
        
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            executor.submit(() -> {
                if(handlers.stream().anyMatch(i -> i.isSupported(recipe))){
                    VirtualRecipe vr = new VirtualRecipe(
                            recipe.getId(),
                            recipe.getType().toString(),
                            recipe.getIngredients(),
                            recipe.getResultItem(level.registryAccess()),
                            recipe
                    );
                    synchronized (recipeMap){
                        recipeMap.put(vr.id, vr);
                    }
                    ItemStack result = vr.output;
                    if (!result.isEmpty()) {
                        synchronized (recipesByOutput) {
                            recipesByOutput.computeIfAbsent(result.getItem(), k -> new ArrayList<>()).add(vr);
                        }
                    }

                    for (Ingredient ing : vr.inputs) {
                        if (!ing.isEmpty()) {
                            addUniqueIngredient(ing);
                        }
                    }

                    synchronized (recipeTypes) {
                        if(recipeTypes.stream().noneMatch(i -> i.toString().equals(recipe.getType().toString()))) {
                            recipeTypes.add(recipe.getType());
                        }
                    }
                }
            });
        }

        for(RecipeType<?> t : recipeTypes) {
            System.out.println("*** Chaos recipe type: " + t.toString());
        }
        
        executor.shutdown();
        
        while(!executor.isTerminated()) {
            try {
                if(executor.awaitTermination(10000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    break;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                executor.shutdownNow();
                break;
            }
        }
    }
    
    public Set<Ingredient> getAlternatives(VirtualRecipe recipe, Ingredient ingredient, IngredientFilter filter) {
        Set<Ingredient> alternatives = new HashSet<>();
        boolean filteringEMC = filter.percentHigher != null || filter.percentLower != null;
        long emc = filteringEMC ? ingredientEmcCache.getOrDefault(ingredient, EMCHelper.getEmcValue(ingredient)) : 1;
        if (emc <= 0) return alternatives;
        
        for(Ingredient i : uniqueIngredients) {
            long iemc = ingredientEmcCache.getOrDefault(i, EMCHelper.getEmcValue(i));
            
            if(filteringEMC) {
                if(iemc == 0) continue;

                if(filter.percentLower != null && iemc <= emc * filter.percentLower) {
                    continue;
                }
                
                if(filter.percentHigher != null && iemc >= emc * filter.percentHigher) {
                    continue;
                }
                
                if(filter.tagged != null) {
                    boolean isTag = isTagIngredient(i);
                    if(filter.tagged != isTag) {
                        continue;
                    }
                }
                
                if(filter.multipleItems != null) {
                    boolean hasMultiple = i.getItems().length > 1;
                    if(filter.multipleItems != hasMultiple) {
                        continue;
                    }
                }
                
                if(wouldCreateCircularReference(recipe, i)){
                    continue;
                }
                
                alternatives.add(i);
            }
        }
        
        return alternatives;
    }
    
    
    public boolean isTagIngredient(Ingredient ingredient) {
        if (ingredient.isEmpty()) return false;

        JsonElement json = ingredient.toJson();

        if (json.isJsonObject()) {
            return json.getAsJsonObject().has("tag");
        }

        if (json.isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray()) {
                if (element.isJsonObject() && element.getAsJsonObject().has("tag")) {
                    return true;
                }
            }
        }

        return false;
    }
    
    private void addUniqueIngredient(Ingredient incoming) {
        boolean exists = uniqueIngredients.stream()
                .anyMatch(existing -> existing.toJson().equals(incoming.toJson()));

        if (!exists) {
            uniqueIngredients.add(incoming);
            ingredientEmcCache.put(incoming, EMCHelper.getEmcValue(incoming));
        }
    }

    public void modifyIngredient(ResourceLocation id, int slotIndex, Ingredient newIngredient) {
        VirtualRecipe recipe = recipeMap.get(id);
        if (recipe != null) {
            recipe.inputs.set(slotIndex, newIngredient);
            recipe.isModified = true;
        }
    }

    public boolean wouldCreateCircularReference(VirtualRecipe recipe, Ingredient potentialIngredient) {
        Item outputItem = recipe.output.getItem();
        Set<Item> visited = new HashSet<>();

        for (ItemStack stack : potentialIngredient.getItems()) {
            if (isItemInDependencyChain(stack.getItem(), outputItem, visited)) {
                return true;
            }
        }
        return false;
    }

    private boolean isItemInDependencyChain(Item currentItem, Item targetOutput, Set<Item> visited) {
        if (currentItem == targetOutput) return true;
        if (!visited.add(currentItem)) return false;
        List<VirtualRecipe> recipes = recipesByOutput.get(currentItem);

        if (recipes != null) {
            for (VirtualRecipe vr : recipes) {
                for (Ingredient ing : vr.inputs) {
                    if (ing.isEmpty()) continue;

                    for (ItemStack stack : ing.getItems()) {
                        if (isItemInDependencyChain(stack.getItem(), targetOutput, visited)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public static JsonElement serializeIngredient(Ingredient ing) {
        ItemStack[] stacks = ing.getItems();

        if (stacks.length == 1 && stacks[0].hasTag()) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "forge:nbt");
            json.addProperty("item", BuiltInRegistries.ITEM.getKey(stacks[0].getItem()).toString());
            json.addProperty("nbt", stacks[0].getTag().toString());
            return json;
        }

        // Fallback: Use vanilla serialization (handles Tags, plain items, lists)
        return ing.toJson();
    }

    public void exportToDataPack(Level level) {
        net.minecraft.server.MinecraftServer server = level.getServer();
        if (server == null) {
            System.err.println("Cannot export datapack: Server is null (Are you on the client?)");
            return;
        }

        Path packDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR)
                .resolve("auram_chaos_pack");

        try {
            if(Files.exists(packDir)) {
                Files.walk(packDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            }
            
            Files.createDirectories(packDir);
            String metadata = "{\n" +
                    "  \"pack\": {\n" +
                    "    \"pack_format\": 15,\n" +
                    "    \"description\": \"Auram Chaos Generated Recipes\"\n" +
                    "  }\n" +
                    "}";
            Files.writeString(packDir.resolve("pack.mcmeta"), metadata);

            int exportedCount = 0;
            for (VirtualRecipe recipe : recipeMap.values()) {
                if (!recipe.isModified) continue;

                for (RecipeHandler handler : handlers) {
                    if (handler.isSupported(recipe.originalRecipe)) {
                        String jsonContent = handler.write(recipe, (ing) -> ing.toJson().toString());
                        Path recipePath = packDir.resolve("data")
                                .resolve(recipe.id.getNamespace())
                                .resolve("recipes")
                                .resolve(recipe.id.getPath() + ".json");

                        Files.createDirectories(recipePath.getParent());
                        Files.writeString(recipePath, jsonContent);
                        exportedCount++;
                        break;
                    }
                }
            }

            for (VirtualRecipe recipe : createdRecipes.values()) {
                if (!recipe.isCreated) continue;

                for (RecipeHandler handler : handlers) {
                    if (handler.isSupported(recipe.originalRecipe)) {
                        String jsonContent = handler.write(recipe, (ing) -> ing.toJson().toString());
                        Path recipePath = packDir.resolve("data")
                                .resolve(recipe.id.getNamespace())
                                .resolve("recipes")
                                .resolve(recipe.id.getPath() + ".json");

                        Files.createDirectories(recipePath.getParent());
                        Files.writeString(recipePath, jsonContent);
                        exportedCount++;
                        break;
                    }
                }
            }
            
            System.out.println("Exported " + exportedCount + " chaos recipes to datapack.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
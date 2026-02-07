package art.arcane.auram.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class RecipeCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("auram");
    private static final File CACHE_FILE = CONFIG_DIR.resolve("rock_recipes.json").toFile();
    public static final Map<String, CachedRecipe> RECIPE_MAP = new HashMap<>();

    public static void load() {
        if (!CACHE_FILE.exists()) return;

        try (FileReader reader = new FileReader(CACHE_FILE)) {
            Type type = new TypeToken<Map<String, CachedRecipe>>(){}.getType();
            Map<String, CachedRecipe> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                RECIPE_MAP.putAll(loaded);
                System.out.println("Auram: Loaded " + RECIPE_MAP.size() + " cached recipes from file.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save(Map<ResourceLocation, CachedRecipe> newMappings) {
        Map<String, CachedRecipe> stringMap = new HashMap<>();
        newMappings.forEach((k, v) -> stringMap.put(k.toString(), v));
        
        if (stringMap.equals(RECIPE_MAP)) return;

        try {
            if (!CONFIG_DIR.toFile().exists()) CONFIG_DIR.toFile().mkdirs();
            
            try (FileWriter writer = new FileWriter(CACHE_FILE)) {
                GSON.toJson(stringMap, writer);
                System.out.println("Auram: Updated recipe cache file.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
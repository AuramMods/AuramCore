package art.arcane.auram;

import art.arcane.auram.util.RecipeCache;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(modid = Auram.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Data implements PackResources {
    public static final String PACK_ID = "auram_generated_recipes";
    public static int VIRTUAL_FILE_COUNT = 0;
    private final byte[] mcmetaBytes;
    private final Map<ResourceLocation, byte[]> virtualFiles = new HashMap<>();

    public Data() {
        JsonObject meta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 15); // 15 is for 1.20.1
        pack.addProperty("description", "Auram Generated Recipes");
        meta.add("pack", pack);
        this.mcmetaBytes = meta.toString().getBytes(StandardCharsets.UTF_8);
        generateRecipes();
    }

    private void generateRecipes() {
        if (RecipeCache.RECIPE_MAP.isEmpty()) {
            RecipeCache.load();
        }

        RecipeCache.RECIPE_MAP.forEach((rockKey, cachedRecipe) -> {
            String rockPath = ResourceLocation.parse(rockKey).getPath();
            ResourceLocation fileLoc = ResourceLocation.tryBuild(Auram.MODID, "recipes/gen_" + rockPath + ".json");
            JsonObject json = new JsonObject();
            json.addProperty("type", "minecraft:crafting_shaped");
            json.addProperty("category", "misc"); // Good practice to include category

            JsonArray pattern = new JsonArray();
            pattern.add("RR");
            pattern.add("RR");
            json.add("pattern", pattern);

            JsonObject key = new JsonObject();
            JsonObject itemKey = new JsonObject();
            itemKey.addProperty("item", rockKey);
            key.add("R", itemKey);
            json.add("key", key);

            JsonObject result = new JsonObject();
            result.addProperty("item", cachedRecipe.drop);

            if (cachedRecipe.count > 1) {
                result.addProperty("count", cachedRecipe.count);
            }
            
            json.add("result", result);

            virtualFiles.put(fileLoc, json.toString().getBytes(StandardCharsets.UTF_8));
        });

        VIRTUAL_FILE_COUNT = virtualFiles.size();
    }
    
    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type == PackType.SERVER_DATA && virtualFiles.containsKey(location)) {
            return () -> new ByteArrayInputStream(virtualFiles.get(location));
        }
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... strings) {
        if (strings.length > 0 && strings[0].equals("pack.mcmeta")) {
            return () -> new ByteArrayInputStream(mcmetaBytes);
        }
        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, PackResources.ResourceOutput output) {
        if (type == PackType.SERVER_DATA && namespace.equals(Auram.MODID)) {
            virtualFiles.forEach((loc, bytes) -> {
                if (loc.getPath().startsWith(path)) {
                    output.accept(loc, () -> new ByteArrayInputStream(bytes));
                }
            });
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> set = new HashSet<>();
        if (type == PackType.SERVER_DATA) set.add(Auram.MODID);
        return set;
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        if (deserializer.getMetadataSectionName().equals("pack")) {
            try (InputStream stream = new ByteArrayInputStream(mcmetaBytes)) {
                return net.minecraft.server.packs.AbstractPackResources.getMetadataFromStream(deserializer, stream);
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public String packId() {
        return PACK_ID;
    }

    @Override
    public boolean isBuiltin() {
        return true;
    } 

    @Override
    public void close() {}

    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA) {
            event.addRepositorySource((Consumer<Pack> consumer) -> {
                Pack pack = Pack.readMetaAndCreate(
                        PACK_ID,
                        Component.literal("Auram Generated Recipes"),
                        true,
                        (id) -> new Data(),
                        PackType.SERVER_DATA,
                        Pack.Position.TOP,
                        PackSource.BUILT_IN
                );
                if (pack != null) {
                    consumer.accept(pack);
                }
            });
        }
    }
}
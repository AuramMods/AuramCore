package art.arcane.auram;

import art.arcane.auram.util.CachedRecipe;
import art.arcane.auram.util.LootHelper;
import art.arcane.auram.util.RecipeCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Auram.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class Server {
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        refreshRecipeCache(event.getServer());
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                refreshRecipeCache(server);
            }
        }
    }

    private static void refreshRecipeCache(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Map<ResourceLocation, CachedRecipe> newMappings = new HashMap<>();

        Auram.BYPASS_ROCK_GENERATION = true;
        
        try {
            Auram.ROCK_ITEM_TO_ORE_BLOCK.forEach((rockItem, oreBlock) -> {
                ItemStack drop = LootHelper.getMaxedFakeDrop(oreBlock, level);

                if (!drop.isEmpty()) {
                    ResourceLocation rockId = ForgeRegistries.ITEMS.getKey(rockItem);
                    ResourceLocation dropId = ForgeRegistries.ITEMS.getKey(drop.getItem());

                    if (rockId != null && dropId != null && !rockId.equals(dropId)) {
                        newMappings.put(rockId, new CachedRecipe(dropId.toString(), drop.getCount()));
                    }
                }
            });
        } finally {
            Auram.BYPASS_ROCK_GENERATION = false;
        }

        RecipeCache.save(newMappings);
        RecipeCache.RECIPE_MAP.clear();
        newMappings.forEach((k, v) -> RecipeCache.RECIPE_MAP.put(k.toString(), v));

        System.out.println("Auram: Calculated " + newMappings.size() + " recipes. Virtual Pack Count: " + Data.VIRTUAL_FILE_COUNT);

        if (newMappings.size() > 0 && Data.VIRTUAL_FILE_COUNT == 0) {
            System.out.println("Auram: First run detected. Please run /reload or Restart to activate recipes.");
        }
    }
}
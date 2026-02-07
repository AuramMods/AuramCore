package art.arcane.auram.util;

import art.arcane.auram.Auram;
import art.arcane.auram.item.RockCatalyst;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = Auram.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RockPickupHandler {

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        ItemStack pickedUp = event.getItem().getItem();
        
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(pickedUp.getItem());
        if (!RecipeCache.RECIPE_MAP.containsKey(id.toString())) return;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack invStack = player.getInventory().getItem(i);
            
            if (invStack.getItem() instanceof RockCatalyst catalystItem) {
                int consumed = catalystItem.tryAddRock(invStack, pickedUp, player);
                
                if (consumed > 0) {
                    pickedUp.shrink(consumed);

                    if (pickedUp.isEmpty()) {
                        event.setCanceled(true); 
                        event.getItem().discard();
                    }
                    return;
                }
            }
        }
    }
}
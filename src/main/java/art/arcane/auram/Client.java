package art.arcane.auram;

import art.arcane.auram.item.GearItem;
import art.arcane.auram.util.ColorHelper;
import art.arcane.auram.util.ComponentItem;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Mod.EventBusSubscriber(modid = Auram.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class Client {
    private static final Map<Item, Integer> ROCK_COLOR_CACHE = new HashMap<>();
    private static final Map<String, Integer> COMPONENT_COLOR_CACHE = new HashMap<>();

    @SubscribeEvent
    public static void onRegisterGeometry(ModelEvent.RegisterAdditional event) {
        event.register(Objects.requireNonNull(ResourceLocation.tryBuild(Auram.MODID, "item/rock_ore")));
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> modelRegistry = event.getModels();
        ResourceLocation parentLocation = ResourceLocation.tryBuild(Auram.MODID, "item/rock_ore");
        BakedModel parentModel = modelRegistry.get(parentLocation);

        if (parentModel == null) return;

        for (ResourceLocation rockId : Auram.GENERATED_ROCKS) {
            ModelResourceLocation itemModelRL = new ModelResourceLocation(rockId, "inventory");
            modelRegistry.put(itemModelRL, parentModel);
        }
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        ItemColor dynamicColorHandler = (stack, tintIndex) -> {
            if(tintIndex == 0) {
                if (stack.getItem() instanceof ComponentItem) {
                    if (stack.hasTag() && stack.getTag().contains(ComponentItem.NBT_MATERIAL_KEY)) {
                        String key = stack.getTag().getString(ComponentItem.NBT_MATERIAL_KEY);
                        return COMPONENT_COLOR_CACHE.computeIfAbsent(key, k ->
                                ColorHelper.computeColorFor(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(key)))
                        );
                    }
                }
            }

            if (tintIndex == 1) {
                if (Auram.ROCK_ITEM_TO_ORE_BLOCK.containsKey(stack.getItem())) {
                    Item item = stack.getItem();
                    if (ROCK_COLOR_CACHE.containsKey(item)) {
                        return ROCK_COLOR_CACHE.get(item);
                    }
                    var oreBlock = Auram.ROCK_ITEM_TO_ORE_BLOCK.get(item);
                    if (oreBlock != null) {
                        int color = ColorHelper.getDominantColor(oreBlock);
                        if (color == -1) color = oreBlock.defaultMapColor().col;
                        ROCK_COLOR_CACHE.put(item, color);
                        return color;
                    }
                }
            }
            return -1; 
        };

        for (ResourceLocation rockId : Auram.GENERATED_ROCKS) {
            if (ForgeRegistries.ITEMS.containsKey(rockId)) {
                event.register(dynamicColorHandler, ForgeRegistries.ITEMS.getValue(rockId));
            }
        }

        for(RegistryObject i : Auram.COMPONENTS)
        {
            event.register(dynamicColorHandler, ((RegistryObject<Item>)i).get());
        }   
        
    }
}
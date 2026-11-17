package art.arcane.auram;

import art.arcane.auram.util.ColorHelper;
import art.arcane.auram.util.ComponentItem;
import art.arcane.auram.util.RockHost;
import art.arcane.auram.util.RockSpriteLoader;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterTextureAtlasSpriteLoadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Auram.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class Client {
    private static final Map<Item, Integer> ROCK_COLOR_CACHE = new HashMap<>();
    private static volatile Map<Item, RockHost> ROCK_HOSTS = Map.of();
    private static final Map<String, Integer> COMPONENT_COLOR_CACHE = new HashMap<>();

    @SubscribeEvent
    public static void onRegisterGeometry(ModelEvent.RegisterAdditional event) {
        for (RockHost host : RockHost.values()) {
            event.register(ResourceLocation.tryBuild(Auram.MODID, "item/" + host.model));
        }
    }

    @SubscribeEvent
    public static void onRegisterSpriteLoaders(RegisterTextureAtlasSpriteLoadersEvent event) {
        event.register("rock_host", new RockSpriteLoader());
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> modelRegistry = event.getModels();
        Map<Item, RockHost> hosts = new HashMap<>();
        Auram.ROCK_ITEM_TO_ORE_BLOCK.forEach((rock, ore) -> {
            ResourceLocation oreId = ForgeRegistries.BLOCKS.getKey(ore);
            BakedModel oreModel = modelRegistry.get(BlockModelShaper.stateToModelLocation(ore.defaultBlockState()));
            String texturePath = oreModel == null ? "" : oreModel.getParticleIcon().contents().name().getPath();
            RockHost host = RockHost.detect(oreId.getPath(), texturePath);
            BakedModel parentModel = modelRegistry.get(ResourceLocation.tryBuild(Auram.MODID, "item/" + host.model));
            ResourceLocation rockId = ForgeRegistries.ITEMS.getKey(rock);
            ModelResourceLocation itemModelRL = new ModelResourceLocation(rockId, "inventory");
            if (parentModel != null) modelRegistry.put(itemModelRL, parentModel);
            hosts.put(rock, host);
        });
        ROCK_HOSTS = Map.copyOf(hosts);
    }

    @SubscribeEvent
    public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        // This event runs on the render thread, after the new models are installed.
        ROCK_COLOR_CACHE.clear();
        COMPONENT_COLOR_CACHE.clear();
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
                        int color = ColorHelper.getDominantColor(oreBlock, ROCK_HOSTS.getOrDefault(item, RockHost.STONE));
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

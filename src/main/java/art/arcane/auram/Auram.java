package art.arcane.auram;

import art.arcane.auram.commands.AuramCommands;
import art.arcane.auram.enchantment.ChiselEnchantment;
import art.arcane.auram.item.*;
import art.arcane.auram.loot.RockLootModifier;
import art.arcane.auram.util.RecipeCache;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod(Auram.MODID)
public class Auram {
    public static final String MODID = "auram";
    private static final Logger LOGGER = LogUtils.getLogger();
    public static boolean BYPASS_ROCK_GENERATION = false;
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS = DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);
    public static final RegistryObject<Item> ROCK = ITEMS.register("rock", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> BUNDLED_STICKS = ITEMS.register("bundled_sticks", BundledSticks::new);
    public static final RegistryObject<Item> JOURNEY_MAP = ITEMS.register("journey_map", JourneyMap::new);
    public static final RegistryObject<Item> ENDER_JADE = ITEMS.register("ender_jade", EnderJade::new);
    public static final RegistryObject<Item> ROCK_CRATE = ITEMS.register("rock_catalyst", RockCatalyst::new);
    public static final RegistryObject<Item> GEAR = ITEMS.register("gear", GearItem::new);
    public static final RegistryObject<Item> PLATE = ITEMS.register("plate", PlateItem::new);
    @SuppressWarnings("rawtypes")
    public static final RegistryObject[] COMPONENTS = new RegistryObject[]{GEAR, PLATE};
    public static final RegistryObject<Codec<RockLootModifier>> ROCK_MODIFIER = LOOT_MODIFIERS.register("rock_drops", RockLootModifier.CODEC);
    public static final Map<Item, Block> ROCK_ITEM_TO_ORE_BLOCK = new HashMap<>();
    public static final Map<ResourceLocation, ResourceLocation> ORE_BLOCK_ID_TO_ROCK_ID = new HashMap<>();
    public static final List<ResourceLocation> GENERATED_ROCKS = new ArrayList<>();
    public static final RegistryObject<Enchantment> CHISEL = ENCHANTMENTS.register("chisel", ChiselEnchantment::new);
    
    public static final RegistryObject<CreativeModeTab> AURAM_TAB = CREATIVE_TABS.register("auram_tab", () -> CreativeModeTab.builder()
            .title(Component.literal("Auram"))
            .icon(() -> new ItemStack(ENDER_JADE.get()))
            .displayItems((params, output) -> {
                output.accept(Auram.ENDER_JADE.get());
                output.accept(Auram.JOURNEY_MAP.get());
                output.accept(Auram.BUNDLED_STICKS.get());
                output.accept(Auram.ROCK_CRATE.get());
                
                for (Item item : ForgeRegistries.ITEMS) {
                    ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
                    if (key != null && key.getNamespace().equals(MODID)) {
                        output.accept(item);
                    }
                }
            })
            .build());


    public Auram(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ENCHANTMENTS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        LOOT_MODIFIERS.register(modEventBus);
        RecipeCache.load();
        modEventBus.register(this);
        context.registerConfig(ModConfig.Type.COMMON, AuramConfig.COMMON_SPEC);
        MinecraftForge.EVENT_BUS.register(new AuramCommands());
    } 

    @SubscribeEvent
    public void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.ITEMS)) {
            // Iterate over a snapshot of the block registry
            for (Map.Entry<ResourceKey<Block>, Block> entry : ForgeRegistries.BLOCKS.getEntries()) {
                Block block = entry.getValue();
                ResourceLocation blockId = entry.getKey().location();
                String path = blockId.getPath();
                
                if (path.contains("_ore") || path.endsWith("_ore")) {
                    String namespace = blockId.getNamespace();
                    String newPath;

                    if (namespace.equals("minecraft")) {
                        newPath = path.replace("ore", "rock");
                    } else {
                        newPath = namespace + "_" + path.replace("ore", "rock");
                    }

                    if (newPath.equals(path)) {
                        newPath += "_rock";
                    }

                    newPath = newPath.replace("__", "_");
                    ResourceLocation newId = ResourceLocation.tryBuild(MODID, newPath);

                    event.register(ForgeRegistries.Keys.ITEMS, helper -> {
                        Item.Properties props = new Item.Properties();
                        RockItem rockItem = new RockItem(props);
                        helper.register(newId, rockItem);
                        GENERATED_ROCKS.add(newId);
                        ROCK_ITEM_TO_ORE_BLOCK.put(rockItem, block);
                        ORE_BLOCK_ID_TO_ROCK_ID.put(blockId, newId);
                        LOGGER.info("Generated Rock: {} from {}", newId, blockId);
                    });
                }
            }
        }
    }
}
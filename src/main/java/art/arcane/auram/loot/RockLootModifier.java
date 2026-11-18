package art.arcane.auram.loot;

import art.arcane.auram.Auram;
import art.arcane.auram.util.RockHost;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.concurrent.ThreadLocalRandom;

public class RockLootModifier extends LootModifier {
    private static final TagKey<Block> END_STONE_ORES = TagKey.create(Registries.BLOCK,
            ResourceLocation.parse("forge:ores_in_ground/end_stone"));
    public static final Supplier<Codec<RockLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, RockLootModifier::new)));

    public RockLootModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (Auram.BYPASS_ROCK_GENERATION) {
            return generatedLoot;
        }
        
        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (state == null) return generatedLoot;
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        ResourceLocation rockId = Auram.ORE_BLOCK_ID_TO_ROCK_ID.get(blockId);

        if (rockId == null || !ForgeRegistries.ITEMS.containsKey(rockId)) {
            return generatedLoot; 
        }

        Item rockItem = ForgeRegistries.ITEMS.getValue(rockId);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = switch (miningHost(state, blockId)) {
            case STONE -> random.nextInt(1, 5);
            case DEEPSLATE, NETHERRACK -> random.nextInt(3, 7);
            case END_STONE -> random.nextInt(4, 7);
        };
        ObjectArrayList<ItemStack> newLoot = new ObjectArrayList<>();
        newLoot.add(new ItemStack(rockItem, count));
        return newLoot;
    }

    private static RockHost miningHost(BlockState state, ResourceLocation blockId) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(state.getBlock().asItem());
        // Keep the original bonus for ores whose item ID contains "deepslate".
        if (itemId != null && itemId.toString().contains("deepslate")) return RockHost.DEEPSLATE;

        RockHost host = RockHost.fromPath(blockId.getPath());
        if (host != RockHost.STONE) return host;
        if (state.is(END_STONE_ORES)) return RockHost.END_STONE;
        if (state.is(Tags.Blocks.ORES_IN_GROUND_NETHERRACK)) return RockHost.NETHERRACK;
        return RockHost.STONE;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}

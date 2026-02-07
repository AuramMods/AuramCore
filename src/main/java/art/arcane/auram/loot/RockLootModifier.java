package art.arcane.auram.loot;

import art.arcane.auram.Auram;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.concurrent.ThreadLocalRandom;

public class RockLootModifier extends LootModifier {
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
        int boost = 0;
        
        if (ForgeRegistries.ITEMS.getKey(state.getBlock().asItem()).toString().contains("deepslate")) {
            boost = 2;
        } 
        
        int finalCount =  ThreadLocalRandom.current().nextInt(1+boost, 5+boost);
        ObjectArrayList<ItemStack> newLoot = new ObjectArrayList<>();
        while (finalCount > 0) {
            int stackSize = Math.min(finalCount, 64);
            newLoot.add(new ItemStack(rockItem, stackSize));
            finalCount -= stackSize;
        }

        return newLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
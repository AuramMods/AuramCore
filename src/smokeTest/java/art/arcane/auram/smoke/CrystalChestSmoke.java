package art.arcane.auram.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class CrystalChestSmoke {
    @SuppressWarnings("unchecked")
    static Map<String, Object> run(MinecraftServer server) throws Exception {
        var type = Class.forName("com.progwml6.ironchest.common.block.regular.entity.CrystalChestBlockEntity");
        var state = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse("ironchest:crystal_chest"))
                .orElseThrow().defaultBlockState();
        var create = type.getConstructor(BlockPos.class, BlockState.class);
        var original = create.newInstance(BlockPos.ZERO, state);
        var optimized = create.newInstance(BlockPos.ZERO, state);
        ((BlockEntity) original).setLevel(server.overworld());
        ((BlockEntity) optimized).setLevel(server.overworld());
        var items = type.getMethod("getCurrentItems");
        var originalSlots = (NonNullList<ItemStack>) items.invoke(original);
        var optimizedSlots = (NonNullList<ItemStack>) items.invoke(optimized);
        var before = type.getMethod("sortTopStacks");
        var tick = type.getMethod("tick", Level.class, BlockPos.class, BlockState.class, type.getSuperclass());
        var top = type.getMethod("getTopItems");
        var dirty = type.getDeclaredField("inventoryTouched");
        dirty.setAccessible(true);
        var variety = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).limit(108).toList();
        for (int scenario = 0; scenario < 7; scenario++) {
            for (int slot = 0; slot < originalSlots.size(); slot++) {
                ItemStack stack = switch (scenario) {
                    case 0, 6 -> ItemStack.EMPTY;
                    case 1 -> new ItemStack(Items.COBBLESTONE, 32);
                    case 2 -> new ItemStack(Items.STONE, 1 + slot % 8);
                    default -> new ItemStack(variety.get(scenario == 3 ? slot : slot % 23), 1 + slot % 16);
                };
                if (scenario == 5) stack.getOrCreateTag().putInt("variant", slot % 4);
                originalSlots.set(slot, stack);
                optimizedSlots.set(slot, stack.copy());
            }
            before.invoke(original);
            dirty.setBoolean(optimized, true);
            tick.invoke(null, server.overworld(), BlockPos.ZERO, state, optimized);
            List<ItemStack> expected = (List<ItemStack>) top.invoke(original);
            List<ItemStack> actual = (List<ItemStack>) top.invoke(optimized);
            var expectedData = expected.stream().map(stack -> stack.save(new CompoundTag())).toList();
            var actualData = actual.stream().map(stack -> stack.save(new CompoundTag())).toList();
            if (!expectedData.equals(actualData)) {
                throw new AssertionError("Crystal chest preview changed for scenario " + scenario
                        + ", source stack " + originalSlots.get(0)
                        + ", had items " + type.getMethod("getHadStuff").invoke(original)
                        + ". Expected " + expectedData + ", got " + actualData);
            }
        }
        for (int slot = 0; slot < originalSlots.size(); slot++) {
            var stack = new ItemStack(variety.get(slot), 1 + slot % 16);
            originalSlots.set(slot, stack);
            optimizedSlots.set(slot, stack.copy());
        }
        var oldTimes = new double[21];
        var newTimes = new double[21];
        for (int sample = -5; sample < oldTimes.length; sample++) {
            for (int order = 0; order < 2; order++) {
                boolean reference = (sample + order) % 2 == 0;
                long start = System.nanoTime();
                for (int i = 0; i < 100; i++) {
                    if (reference) {
                        before.invoke(original);
                    } else {
                        dirty.setBoolean(optimized, true);
                        tick.invoke(null, server.overworld(), BlockPos.ZERO, state, optimized);
                    }
                }
                if (sample >= 0) (reference ? oldTimes : newTimes)[sample] = (System.nanoTime() - start) / 1e6;
            }
        }
        Arrays.sort(oldTimes);
        Arrays.sort(newTimes);
        return Map.of("cases", 7, "slots", originalSlots.size(), "batches", oldTimes.length,
                "reference_100_rebuild_median_ms", oldTimes[oldTimes.length / 2],
                "candidate_100_rebuild_median_ms", newTimes[newTimes.length / 2]);
    }
}

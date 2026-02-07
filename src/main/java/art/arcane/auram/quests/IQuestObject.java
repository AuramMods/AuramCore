package art.arcane.auram.quests;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

public interface IQuestObject {
    CompoundTag toNBT();

    Random RANDOM = new Random();

    default String rid() {
        long randomValue = RANDOM.nextLong();
        return String.format("%016X", randomValue).substring(8);
    }

    default String sloc(ItemStack stack) {
        return loc(stack).toString();
    }
    
    default String sloc(Item item) {
        return loc(item).toString();
    }

    default ResourceLocation loc(ItemStack stack) {
        return loc(stack.getItem());
    }

    default ResourceLocation loc(Item i) {
        return BuiltInRegistries.ITEM.getKey(i);
    }
}

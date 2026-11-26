package art.arcane.auram.mixin.regionsunexplored;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

import java.util.IdentityHashMap;
import java.util.Map;

@Pseudo
@Mixin(targets = "net.regions_unexplored.block.compat.FurnaceBurnTimes", remap = false)
abstract class FurnaceFuelLookupMixin {
    @Unique private static volatile Map<Item, Integer> auram$burnTimes;

    @WrapMethod(method = "burnTime")
    private static void auram$useFuelLookup(FurnaceFuelBurnTimeEvent event, Operation<Void> original) {
        Map<Item, Integer> burnTimes = auram$burnTimes;
        if (burnTimes == null) burnTimes = auram$indexFuels(original);
        Integer burnTime = burnTimes.get(event.getItemStack().getItem());
        if (burnTime != null) event.setBurnTime(burnTime);
    }

    @Unique
    private static synchronized Map<Item, Integer> auram$indexFuels(Operation<Void> original) {
        if (auram$burnTimes != null) return auram$burnTimes;

        // Regions Unexplored compares the item against its registered blocks' item forms.
        // Evaluate the original listener once per distinct item form after block registration.
        Map<Item, Integer> burnTimes = new IdentityHashMap<>();
        burnTimes.put(Items.AIR, auram$readBurnTime(Items.AIR, original));
        for (var entry : ForgeRegistries.BLOCKS.getEntries()) {
            if (entry.getKey().location().getNamespace().equals("regions_unexplored")) {
                Item item = entry.getValue().asItem();
                burnTimes.computeIfAbsent(item, fuel -> auram$readBurnTime(fuel, original));
            }
        }
        burnTimes.values().removeIf(time -> time < 0);
        auram$burnTimes = burnTimes;
        return burnTimes;
    }

    @Unique
    private static int auram$readBurnTime(Item item, Operation<Void> original) {
        var probe = new FurnaceFuelBurnTimeEvent(new ItemStack(item), -1, null);
        original.call(probe);
        return probe.getBurnTime();
    }
}

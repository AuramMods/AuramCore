package art.arcane.auram.smoke;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

@Mod.EventBusSubscriber(modid = "auram_patch_smoke")
public final class FuelLookupSmoke {
    private FuelLookupSmoke() {}

    @SubscribeEvent
    public static void checkFuels(ServerStartedEvent event) throws Throwable {
        if (!Boolean.getBoolean("auram.smoke.fuelLookup")) return;

        var listener = MethodHandles.publicLookup().findStatic(
                Class.forName("net.regions_unexplored.block.compat.FurnaceBurnTimes"),
                "burnTime", MethodType.methodType(void.class, FurnaceFuelBurnTimeEvent.class));
        var items = new TreeMap<String, ItemStack>();
        ForgeRegistries.ITEMS.getEntries().forEach(entry ->
                items.put(entry.getKey().location().toString(), new ItemStack(entry.getValue())));
        var directResults = new ArrayList<String>();
        ItemStack matched = null;
        for (var entry : items.entrySet()) {
            var probe = new FurnaceFuelBurnTimeEvent(entry.getValue(), 9876, RecipeType.SMELTING);
            listener.invokeExact(probe);
            directResults.add(entry.getKey() + "\t" + probe.getBurnTime() + "\t" + probe.isCanceled());
            if (probe.isCanceled() && !entry.getValue().isEmpty()) matched = entry.getValue();
        }
        if (matched == null) throw new AssertionError("Fixture has no Regions Unexplored fuel");

        // This is the live event bus, with a higher priority listener and a lower priority
        // listener which receives canceled events. The mod's registration remains untouched.
        final var fuel = matched;
        final var first = fuel.copy();
        final var last = fuel.copy();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (FurnaceFuelBurnTimeEvent probe) -> {
            if (probe.getItemStack() == first) probe.setBurnTime(9876);
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, (FurnaceFuelBurnTimeEvent probe) -> {
            if (probe.getItemStack() == last && probe.isCanceled()) probe.setBurnTime(4321);
        });
        var higherOverride = new FurnaceFuelBurnTimeEvent(first, 0, RecipeType.SMELTING);
        var lowerOverride = new FurnaceFuelBurnTimeEvent(last, 0, RecipeType.SMELTING);
        MinecraftForge.EVENT_BUS.post(higherOverride);
        MinecraftForge.EVENT_BUS.post(lowerOverride);
        if (higherOverride.getBurnTime() != 9876 || lowerOverride.getBurnTime() != 4321) {
            throw new AssertionError("A Forge fuel override was lost");
        }
        var unmatched = new FurnaceFuelBurnTimeEvent(new ItemStack(Items.STONE), 8765, RecipeType.SMELTING);
        listener.invokeExact(unmatched);
        if (unmatched.getBurnTime() != 8765 || unmatched.isCanceled()) {
            throw new AssertionError("Missing fuels must preserve the incoming event");
        }

        var samples = new ArrayList<String>();
        long total = 0;
        int repetitions = Math.max(1, 12_000 / items.size());
        for (int round = 0; round < 12; round++) {
            long start = System.nanoTime();
            for (int repeat = 0; repeat < repetitions; repeat++) {
                for (var stack : items.values()) {
                    var probe = new FurnaceFuelBurnTimeEvent(stack, 9876, RecipeType.SMELTING);
                    listener.invokeExact(probe);
                    total += probe.getBurnTime();
                }
            }
            samples.add(Long.toString(System.nanoTime() - start));
        }
        Files.write(Path.of("fuel-parity.tsv"), directResults);
        Files.write(Path.of("fuel-benchmark.tsv"), samples);
        Files.write(Path.of("fuel-overrides.tsv"), List.of("higher\t9876", "lower\t4321", "unmatched\t8765"));
        System.out.println("AURAM_FUEL_LOOKUP checked " + items.size() + " registry items with checksum " + total);
    }
}

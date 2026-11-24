package art.arcane.auram.vapor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.IntSupplier;

/** Distinguishes a generator consuming furnace fuel from inventory and recipe queries. */
public final class FurnaceFuelUse {
    private static final String FURNACE_FUEL = "auramFurnaceFuel";
    private static final ThreadLocal<FurnaceFuelUse> CONSUMPTION = new ThreadLocal<>();

    private boolean furnaceFuel;

    private FurnaceFuelUse() {}

    public static int consume(BlockEntity machine, IntSupplier consumeFuel) {
        if (!(machine.getLevel() instanceof ServerLevel)) return consumeFuel.getAsInt();
        var previous = CONSUMPTION.get();
        var current = new FurnaceFuelUse();
        CONSUMPTION.set(current);
        try {
            int duration = consumeFuel.getAsInt();
            if (duration > 0) {
                var data = machine.getPersistentData();
                if (data.getBoolean(FURNACE_FUEL) != current.furnaceFuel) {
                    data.putBoolean(FURNACE_FUEL, current.furnaceFuel);
                    machine.setChanged();
                }
            }
            return duration;
        } finally {
            if (previous == null) CONSUMPTION.remove();
            else CONSUMPTION.set(previous);
        }
    }

    public static void resolved(int burnTime) {
        if (burnTime <= 0) return;
        var current = CONSUMPTION.get();
        if (current != null) current.furnaceFuel = true;
    }

    public static void burnTick(BlockEntity machine) {
        if (machine.getPersistentData().getBoolean(FURNACE_FUEL)) MachineVapor.burning(machine);
    }
}

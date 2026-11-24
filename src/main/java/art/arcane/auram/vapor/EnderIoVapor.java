package art.arcane.auram.vapor;

import art.arcane.auram.Auram;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class EnderIoVapor {
    private static final ResourceLocation MACHINE = ResourceLocation.fromNamespaceAndPath(Auram.MODID, "enderio/machine");
    private static final float RATE = 0.08f;

    private EnderIoVapor() {}

    public static void operating(BlockEntity machine) {
        if (machine.getLevel() instanceof ServerLevel level) {
            BlockPos outlet = MachineVapor.outlet(level, machine);
            if (outlet != null) {
                VaporSources.refresh(level, machine.getBlockPos(), new VaporNetwork.Emitter(MACHINE, outlet, RATE));
            }
        }
    }

    /** One-tick machines use a pulse so the server's four-tick emitter update cannot miss their work. */
    public static void operated(BlockEntity machine) {
        if (machine.getLevel() instanceof ServerLevel level) {
            BlockPos outlet = MachineVapor.outlet(level, machine);
            if (outlet != null) Vapor.pulse(level, outlet, RATE);
        }
    }

}

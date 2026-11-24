package art.arcane.auram.vapor;

import art.arcane.auram.Auram;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ThaumaturgeVapor {
    private static final ResourceLocation CRUCIBLE = source("crucible");
    private static final ResourceLocation INFUSION = source("infusion");
    private static final ResourceLocation MACHINE = source("machine");
    private static final float CRUCIBLE_RATE = 0.05f;
    private static final float MACHINE_RATE = 0.10f;
    private static final float INFUSION_BASE = 0.05f;
    private static final float INFUSION_STABILITY_FLOOR = -100.0f;

    private ThaumaturgeVapor() {}

    public static void crucible(BlockEntity crucible) {
        refresh(crucible, CRUCIBLE, CRUCIBLE_RATE, 1);
    }

    public static void cruciblePuff(BlockEntity crucible, int totalAspects) {
        if (totalAspects > 0 && crucible.getLevel() instanceof ServerLevel level) {
            BlockPos vent = MachineVapor.outlet(level, crucible);
            if (vent != null) Vapor.pulse(level, vent, Math.min(1.0f, 0.03f * totalAspects));
        }
    }

    public static void infusion(BlockEntity matrix, float stability) {
        // The matrix's own unrest uses negative stability and reaches its maximum at -100.
        float unrest = Mth.clamp(stability / INFUSION_STABILITY_FLOOR, 0.0f, 1.0f);
        refresh(matrix, INFUSION, Mth.lerp(unrest, INFUSION_BASE, 1.0f), 1);
    }

    public static void machine(BlockEntity machine, boolean active) {
        if (active) machine(machine, 1);
    }

    public static void machine(BlockEntity machine, int ticks) {
        refresh(machine, MACHINE, MACHINE_RATE, ticks);
    }

    private static void refresh(BlockEntity machine, ResourceLocation id, float rate, int ticks) {
        if (machine.getLevel() instanceof ServerLevel level) {
            BlockPos vent = MachineVapor.outlet(level, machine);
            if (vent != null) VaporSources.refresh(level, machine.getBlockPos(), new VaporNetwork.Emitter(id, vent, rate), ticks);
        }
    }

    private static ResourceLocation source(String name) {
        return ResourceLocation.fromNamespaceAndPath(Auram.MODID, "thaumaturge/" + name);
    }
}

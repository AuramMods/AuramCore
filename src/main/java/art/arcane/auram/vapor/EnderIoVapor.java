package art.arcane.auram.vapor;

import art.arcane.auram.Auram;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class EnderIoVapor {
    private static final ResourceLocation MACHINE = ResourceLocation.fromNamespaceAndPath(Auram.MODID, "enderio/machine");
    private static final float RATE = 0.08f;

    private EnderIoVapor() {}

    public static void operating(BlockEntity machine) {
        if (machine.getLevel() instanceof ServerLevel level) {
            BlockPos outlet = outlet(level, machine);
            if (outlet != null) {
                VaporSources.refresh(level, machine.getBlockPos(), new VaporNetwork.Emitter(MACHINE, outlet, RATE));
            }
        }
    }

    /** One-tick machines use a pulse so the server's four-tick emitter update cannot miss their work. */
    public static void operated(BlockEntity machine) {
        if (machine.getLevel() instanceof ServerLevel level) {
            BlockPos outlet = outlet(level, machine);
            if (outlet != null) Vapor.pulse(level, outlet, RATE);
        }
    }

    private static BlockPos outlet(ServerLevel level, BlockEntity machine) {
        BlockPos pos = machine.getBlockPos();
        BlockPos above = pos.above();
        if (VaporDemonstrators.open(level, above)) return above;
        var state = machine.getBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            BlockPos front = pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
            if (VaporDemonstrators.open(level, front)) return front;
        }
        return null;
    }
}

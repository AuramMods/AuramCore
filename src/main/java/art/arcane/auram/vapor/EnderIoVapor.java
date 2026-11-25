package art.arcane.auram.vapor;

import art.arcane.auram.Auram;
import art.arcane.auram.field.ClientFieldSources.Key;
import art.arcane.auram.field.VoxelGrid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class EnderIoVapor {
    static final ResourceLocation MACHINE = ResourceLocation.fromNamespaceAndPath(Auram.MODID, "enderio/machine");
    private static final float RATE = 0.08f;

    private EnderIoVapor() {}

    public static boolean hasVisibleActivity(BlockState state) {
        if (!state.hasProperty(BlockStateProperties.POWERED)) return false;
        var id = state.getBlock().builtInRegistryHolder().key().location();
        if (!id.getNamespace().equals("enderio")) return false;
        return switch (id.getPath()) {
            case "sag_mill", "alloy_smelter", "slice_and_splice", "painting_machine", "soul_engine" -> true;
            default -> false;
        };
    }

    public static void collect(BlockEntity machine, VoxelGrid.Position origin, Map<Key, VaporNetwork.Emitter> result) {
        if (!machine.getBlockState().getValue(BlockStateProperties.POWERED)) return;
        var outlet = MachineVapor.outlet(machine.getLevel(), machine);
        if (outlet == null || !VoxelGrid.BLOCKS.contains(outlet.getX() - origin.x(), outlet.getY() - origin.y(),
                outlet.getZ() - origin.z())) return;
        result.put(new Key(MACHINE, machine.getBlockPos()), new VaporNetwork.Emitter(MACHINE, outlet, RATE));
    }

    public static void operating(BlockEntity machine) {
        if (machine.getLevel() instanceof ServerLevel level) {
            if (hasVisibleActivity(machine.getBlockState()) && !VaporDemonstrators.legacyViewers(level)) return;
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

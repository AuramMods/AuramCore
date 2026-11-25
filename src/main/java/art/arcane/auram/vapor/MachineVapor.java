package art.arcane.auram.vapor;

import art.arcane.auram.Auram;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class MachineVapor {
    private static final ResourceLocation RF = ResourceLocation.fromNamespaceAndPath(Auram.MODID, "rf_input");
    private static final ResourceLocation FUEL = ResourceLocation.fromNamespaceAndPath(Auram.MODID, "furnace_fuel");
    private static final TagKey<Block> RF_TRANSPORT = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Auram.MODID, "rf_transport"));
    private static final TagKey<Block> FURNACE_FUEL_GENERATORS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Auram.MODID, "furnace_fuel_generators"));

    private MachineVapor() {}

    public static void rfInput(ServerLevel level, BlockPos pos, float rate, int ticks) {
        if (!level.hasChunkAt(pos)) return;
        var machine = level.getBlockEntity(pos);
        if (machine == null || machine.isRemoved() || machine.getBlockState().is(RF_TRANSPORT)) return;
        var vent = outlet(level, machine);
        if (vent != null) VaporSources.refresh(level, pos, new VaporNetwork.Emitter(RF, vent, rate), ticks);
    }

    public static void burning(BlockEntity machine) {
        if (machine.getLevel() instanceof ServerLevel level) {
            var vent = outlet(level, machine);
            if (vent != null) VaporSources.refresh(level, machine.getBlockPos(), new VaporNetwork.Emitter(FUEL, vent, 0.12f));
        }
    }

    public static void burningTaggedGenerator(BlockEntity machine) {
        if (machine.getBlockState().is(FURNACE_FUEL_GENERATORS)) burning(machine);
    }

    static BlockPos outlet(Level level, BlockEntity machine) {
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

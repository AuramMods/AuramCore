package art.arcane.auram.vapor;

import art.arcane.auram.Auram;
import art.arcane.auram.field.ClientFieldSources.Key;
import art.arcane.auram.field.VoxelGrid;
import art.arcane.auram.tconstruct.CastingCooling;
import art.arcane.auram.tconstruct.CastingRecipeState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.tconstruct.smeltery.block.controller.ControllerBlock;
import slimeknights.tconstruct.smeltery.block.controller.HeaterBlock;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.ChannelBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;

import java.util.Map;

public final class TinkersVapor {
    private static final ResourceLocation HEATER = source("heater");
    private static final ResourceLocation CONTROLLER = source("controller");
    private static final ResourceLocation SMELTERY = source("smeltery");
    private static final ResourceLocation FAUCET = source("faucet");
    private static final ResourceLocation CASTING = source("casting");
    private static final ResourceLocation CHANNEL = source("channel");
    private static final float HEAT_RATE = 0.12f;
    private static final float CONTROLLER_RATE = 0.08f;
    private static final float CASTING_RATE = 0.02f;
    private static final float CHANNEL_RATE = 0.005f;
    private static final int BOILING_WATER_KELVIN = 373;

    private TinkersVapor() {}

    public static boolean isVisibleMachine(BlockEntity machine) {
        return machine instanceof HeatingStructureBlockEntity || machine instanceof CastingBlockEntity
                || machine instanceof ChannelBlockEntity;
    }

    static boolean clientOwned(ResourceLocation id) {
        return id.equals(SMELTERY) || id.equals(CASTING) || id.equals(CHANNEL);
    }

    public static void collect(BlockEntity machine, VoxelGrid.Position origin, Map<Key, VaporNetwork.Emitter> result) {
        var level = machine.getLevel();
        if (level == null) return;
        var pos = machine.getBlockPos();
        if (machine instanceof HeatingStructureBlockEntity controller) {
            var structure = controller.getStructure();
            var state = controller.getBlockState();
            if (structure == null || !state.hasProperty(ControllerBlock.IN_STRUCTURE)
                    || !state.getValue(ControllerBlock.IN_STRUCTURE) || controller.getTank().getContained() <= 0) return;
            var min = structure.getMinInside();
            var max = structure.getMaxInside();
            int y = min.getY();
            if (y < origin.y() || y >= origin.y() + VoxelGrid.WINDOW_BLOCKS) return;
            for (int x = Math.max(min.getX(), origin.x()); x <= Math.min(max.getX(), origin.x()+VoxelGrid.WINDOW_BLOCKS-1); x++) {
                for (int z = Math.max(min.getZ(), origin.z()); z <= Math.min(max.getZ(), origin.z()+VoxelGrid.WINDOW_BLOCKS-1); z++) {
                    var cell = new BlockPos(x, y, z);
                    if (level.hasChunkAt(cell) && level.isEmptyBlock(cell)) {
                        result.put(new Key(SMELTERY, cell), new VaporNetwork.Emitter(SMELTERY, cell, HEAT_RATE));
                    }
                }
            }
        } else if (machine instanceof CastingBlockEntity casting && machine instanceof CastingRecipeState recipe
                && recipe.auram$hasVisibleRecipe() && !casting.getTank().isEmpty()) {
            visibleVent(level, pos, CASTING, CASTING_RATE * CastingCooling.remainingHeat(casting), origin, result);
        } else if (machine instanceof ChannelBlockEntity channel && !channel.getFluid().isEmpty()) {
            visibleVent(level, pos, CHANNEL, CHANNEL_RATE, origin, result);
        }
    }

    private static void visibleVent(Level level, BlockPos pos, ResourceLocation id, float rate,
                                    VoxelGrid.Position origin, Map<Key, VaporNetwork.Emitter> result) {
        if (rate <= 0f || !VoxelGrid.BLOCKS.contains(pos.getX()-origin.x(), pos.getY()-origin.y(), pos.getZ()-origin.z())
                || !VaporDemonstrators.open(level, pos)) return;
        result.put(new Key(id, pos), new VaporNetwork.Emitter(id, pos, rate));
    }

    public static void controller(ServerLevel level, BlockPos pos, boolean burning, FluidStack contents) {
        BlockPos heaterPos = pos.below();
        var heater = level.getBlockState(heaterPos);
        if (burning && heater.getBlock() instanceof HeaterBlock) {
            // The melter covers the top of the heater. Vent from its lit front face.
            BlockPos vent = heaterPos.relative(heater.getValue(BlockStateProperties.HORIZONTAL_FACING));
            emit(level, HEATER, heaterPos, vent, HEAT_RATE);
        }
        if (burning || (!contents.isEmpty()
                && contents.getFluid().getFluidType().getTemperature(contents) >= BOILING_WATER_KELVIN)) {
            emit(level, CONTROLLER, pos, pos.above(), CONTROLLER_RATE);
        }
    }

    public static void smelteryFloor(ServerLevel level, BlockPos min, BlockPos max) {
        for (BlockPos pos : BlockPos.betweenClosed(min.getX(), min.getY(), min.getZ(),
                                                  max.getX(), min.getY(), max.getZ())) {
            if (level.hasChunkAt(pos) && level.isEmptyBlock(pos)) {
                BlockPos origin = pos.immutable();
                VaporSources.refresh(level, origin, new VaporNetwork.Emitter(SMELTERY, origin, HEAT_RATE));
            }
        }
    }

    public static void faucet(ServerLevel level, BlockPos pos) {
        emit(level, FAUCET, pos, pos, CASTING_RATE);
    }

    public static void casting(ServerLevel level, BlockPos pos, float heat) {
        if (heat > 0.0f) emit(level, CASTING, pos, pos, CASTING_RATE * heat);
    }

    public static void channel(ServerLevel level, BlockPos pos) {
        emit(level, CHANNEL, pos, pos, CHANNEL_RATE);
    }

    private static void emit(ServerLevel level, ResourceLocation id, BlockPos anchor, BlockPos outlet, float rate) {
        if (VaporDemonstrators.open(level, outlet)) {
            VaporSources.refresh(level, anchor, new VaporNetwork.Emitter(id, outlet, rate));
        }
    }

    private static ResourceLocation source(String name) {
        return ResourceLocation.fromNamespaceAndPath(Auram.MODID, "tconstruct/" + name);
    }
}

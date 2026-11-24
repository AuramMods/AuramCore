package art.arcane.auram.vapor;

import art.arcane.auram.Auram;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.tconstruct.smeltery.block.controller.HeaterBlock;

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

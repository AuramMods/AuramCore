package art.arcane.auram.mixin.tconstruct;

import art.arcane.auram.vapor.TinkersVapor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.fluid.FluidTankAnimated;
import slimeknights.tconstruct.smeltery.block.controller.ControllerBlock;
import slimeknights.tconstruct.smeltery.block.entity.controller.AlloyerBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.MelterBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.module.SolidFuelModule;

@Mixin(value = {MelterBlockEntity.class, AlloyerBlockEntity.class}, remap = false)
abstract class VaporHeatingControllerMixin {
    @Shadow(remap = false) public abstract SolidFuelModule getFuelModule();
    @Shadow(remap = false) public abstract FluidTankAnimated getTank();

    @Inject(method = "tick", at = @At("RETURN"))
    private void auram$vapor(Level world, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (world instanceof ServerLevel level && state.hasProperty(ControllerBlock.IN_STRUCTURE)
                && state.getValue(ControllerBlock.IN_STRUCTURE)) {
            TinkersVapor.controller(level, pos, getFuelModule().hasFuel(), getTank().getFluid());
        }
    }
}

package art.arcane.auram.mixin.tconstruct;

import art.arcane.auram.vapor.TinkersVapor;
import art.arcane.auram.vapor.VaporDemonstrators;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.smeltery.block.controller.ControllerBlock;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;

@Mixin(value = HeatingStructureBlockEntity.class, remap = false)
abstract class VaporSmelteryMixin {
    @Inject(method = "serverTick", at = @At("RETURN"))
    private void auram$vapor(Level world, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (world instanceof ServerLevel level && VaporDemonstrators.legacyViewers(level)) {
            var controller = (HeatingStructureBlockEntity) (Object) this;
            var structure = controller.getStructure();
            var current = controller.getBlockState();
            if (structure != null && current.hasProperty(ControllerBlock.IN_STRUCTURE)
                    && current.getValue(ControllerBlock.IN_STRUCTURE) && controller.getTank().getContained() > 0) {
                TinkersVapor.smelteryFloor(level, structure.getMinInside(), structure.getMaxInside());
            }
        }
    }
}

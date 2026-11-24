package art.arcane.auram.mixin.tconstruct;

import art.arcane.auram.vapor.TinkersVapor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import slimeknights.tconstruct.smeltery.block.FaucetBlock;
import slimeknights.tconstruct.smeltery.block.component.SearedDrainBlock;
import slimeknights.tconstruct.smeltery.block.entity.FaucetBlockEntity;

@Mixin(value = FaucetBlockEntity.class, remap = false)
abstract class VaporFaucetMixin {
    @WrapOperation(method = "pour", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraftforge/fluids/capability/IFluidHandler;fill(Lnet/minecraftforge/fluids/FluidStack;Lnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;)I"))
    private int auram$vapor(IFluidHandler output, FluidStack fluid, IFluidHandler.FluidAction action, Operation<Integer> original) {
        int accepted = original.call(output, fluid, action);
        var faucet = (FaucetBlockEntity) (Object) this;
        if (accepted > 0 && faucet.getLevel() instanceof ServerLevel level) {
            var pos = faucet.getBlockPos();
            var intake = pos.relative(faucet.getBlockState().getValue(FaucetBlock.FACING));
            if (level.hasChunkAt(intake) && level.getBlockState(intake).getBlock() instanceof SearedDrainBlock) {
                TinkersVapor.faucet(level, pos);
            }
        }
        return accepted;
    }
}

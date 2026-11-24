package art.arcane.auram.mixin.enderio;

import art.arcane.auram.rf.RfFlow;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.enderio.enderio.foundation.energy.PoweredMachineEnergyStorage", remap = false)
abstract class EnergyInputVaporMixin {
    @Unique private BlockEntity auram$machine;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void auram$rememberMachine(@Coerce Object machine, CallbackInfo callback) {
        auram$machine = (BlockEntity) machine;
    }

    @Inject(method = "receiveEnergy", at = @At("RETURN"))
    private void auram$acceptedEnergy(int requested, boolean simulate, CallbackInfoReturnable<Integer> result) {
        if (!simulate) RfFlow.recordAcceptedInput(auram$machine, result.getReturnValueI());
    }
}

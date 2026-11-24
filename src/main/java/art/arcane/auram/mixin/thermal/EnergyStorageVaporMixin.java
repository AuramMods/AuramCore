package art.arcane.auram.mixin.thermal;

import art.arcane.auram.rf.RfFlow;
import art.arcane.auram.rf.RfInputOwner;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "cofh.lib.common.energy.EnergyStorageCoFH", remap = false)
abstract class EnergyStorageVaporMixin implements RfInputOwner {
    @Unique private BlockEntity auram$machine;

    @Override
    public void auram$setRfInputOwner(BlockEntity machine) {
        auram$machine = machine;
    }

    @Inject(method = "receiveEnergy", at = @At("RETURN"))
    private void auram$acceptedEnergy(int requested, boolean simulate, CallbackInfoReturnable<Integer> result) {
        if (!simulate && auram$machine != null) RfFlow.recordAcceptedInput(auram$machine, result.getReturnValueI());
    }
}

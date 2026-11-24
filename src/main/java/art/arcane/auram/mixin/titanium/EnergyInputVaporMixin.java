package art.arcane.auram.mixin.titanium;

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
@Mixin(targets = "com.hrznstudio.titanium.component.energy.EnergyStorageComponent", remap = false)
abstract class EnergyInputVaporMixin {
    @Unique private BlockEntity auram$machine;

    @Inject(method = "setComponentHarness", at = @At("TAIL"))
    private void auram$rememberMachine(@Coerce Object harness, CallbackInfo callback) {
        auram$machine = harness instanceof BlockEntity machine ? machine : null;
    }

    @Inject(method = "receiveEnergy", at = @At("RETURN"))
    private void auram$acceptedEnergy(int requested, boolean simulate, CallbackInfoReturnable<Integer> result) {
        if (!simulate && auram$machine != null) RfFlow.recordAcceptedInput(auram$machine, result.getReturnValueI());
    }
}

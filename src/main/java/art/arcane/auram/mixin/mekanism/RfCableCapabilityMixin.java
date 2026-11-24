package art.arcane.auram.mixin.mekanism;

import art.arcane.auram.rf.mekanism.MekanismCableOwner;
import art.arcane.auram.rf.mekanism.MekanismRf;
import mekanism.api.Action;
import mekanism.api.math.FloatingLong;
import mekanism.common.capabilities.energy.DynamicStrictEnergyHandler;
import mekanism.common.tile.transmitter.TileEntityUniversalCable;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DynamicStrictEnergyHandler.class, remap = false)
abstract class RfCableCapabilityMixin implements MekanismCableOwner {
    @Unique private TileEntityUniversalCable auram$cable;

    @Override
    public void auram$bindCable(TileEntityUniversalCable cable) {
        auram$cable = cable;
    }

    @Inject(method = "insertEnergy", at = @At("RETURN"))
    private void auram$observeInput(int container, FloatingLong amount, Direction side, Action action,
                                   CallbackInfoReturnable<FloatingLong> callback) {
        if (auram$cable != null && action == Action.EXECUTE) {
            MekanismRf.inserted(auram$cable.getTransmitter(), side, amount.subtract(callback.getReturnValue()));
        }
    }

    @Inject(method = "extractEnergy", at = @At("RETURN"))
    private void auram$observeExtraction(int container, FloatingLong amount, Direction side, Action action,
                                        CallbackInfoReturnable<FloatingLong> callback) {
        if (auram$cable != null && action == Action.EXECUTE) {
            MekanismRf.extracted(auram$cable.getTransmitter(), side, callback.getReturnValue());
        }
    }
}

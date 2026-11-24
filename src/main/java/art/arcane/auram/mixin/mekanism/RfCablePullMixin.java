package art.arcane.auram.mixin.mekanism;

import art.arcane.auram.rf.mekanism.MekanismRf;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.common.content.network.transmitter.UniversalCable;
import mekanism.common.lib.transmitter.ConnectionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = UniversalCable.class, remap = false)
abstract class RfCablePullMixin {
    @WrapOperation(method = "pullFromAcceptors", at = @At(value = "INVOKE", ordinal = 1, target =
            "Lmekanism/api/energy/IStrictEnergyHandler;extractEnergy(Lmekanism/api/math/FloatingLong;Lmekanism/api/Action;)Lmekanism/api/math/FloatingLong;"))
    private FloatingLong auram$observePull(IStrictEnergyHandler handler, FloatingLong amount, Action action,
                                           Operation<FloatingLong> original) {
        FloatingLong extracted = original.call(handler, amount, action);
        if (!extracted.isZero()) {
            UniversalCable cable = (UniversalCable) (Object) this;
            for (var side : cable.getConnections(ConnectionType.PULL)) {
                if (cable.getAcceptor(side).orElse(null) == handler) {
                    MekanismRf.inserted(cable, side, extracted);
                    break;
                }
            }
        }
        return extracted;
    }
}

package art.arcane.auram.mixin.mekanism;

import art.arcane.auram.rf.mekanism.MekanismEnergyTarget;
import art.arcane.auram.rf.mekanism.MekanismRf;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.content.network.EnergyNetwork;
import mekanism.common.content.network.distribution.EnergyAcceptorTarget;
import mekanism.common.lib.transmitter.acceptor.NetworkAcceptorCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EnergyNetwork.class, remap = false)
abstract class RfEnergyNetworkMixin {
    @WrapOperation(method = "tickEmit", at = @At(value = "NEW",
            target = "(I)Lmekanism/common/content/network/distribution/EnergyAcceptorTarget;"))
    private EnergyAcceptorTarget auram$observeOutput(int expectedSize, Operation<EnergyAcceptorTarget> original) {
        EnergyNetwork network = (EnergyNetwork) (Object) this;
        @SuppressWarnings("unchecked")
        var cache = (NetworkAcceptorCache<IStrictEnergyHandler>) ((RfNetworkAccess) network).auram$acceptorCache();
        return new MekanismEnergyTarget(expectedSize, network, cache);
    }

    @Inject(method = "onUpdate", at = @At("RETURN"))
    private void auram$clearDrainedNetwork(CallbackInfo callback) {
        EnergyNetwork network = (EnergyNetwork) (Object) this;
        if (network.getBuffer().isZero()) MekanismRf.drained(network);
    }
}

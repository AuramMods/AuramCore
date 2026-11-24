package art.arcane.auram.rf.mekanism;

import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.common.content.network.EnergyNetwork;
import mekanism.common.content.network.distribution.EnergyAcceptorTarget;
import mekanism.common.lib.distribution.SplitInfo;
import mekanism.common.lib.transmitter.acceptor.NetworkAcceptorCache;

import java.util.IdentityHashMap;
import java.util.Map;

public final class MekanismEnergyTarget extends EnergyAcceptorTarget {
    private final EnergyNetwork network;
    private final Map<IStrictEnergyHandler, MekanismRf.Endpoint> endpoints = new IdentityHashMap<>();

    public MekanismEnergyTarget(int expectedSize, EnergyNetwork network, NetworkAcceptorCache<IStrictEnergyHandler> cache) {
        super(expectedSize);
        this.network = network;
        for (var entry : cache.getAcceptorEntrySet()) {
            var machine = entry.getKey();
            entry.getValue().forEach((side, optional) -> optional.ifPresent(handler -> {
                // Shared handlers with several ports do not identify which one was paid.
                if (endpoints.containsKey(handler)) endpoints.put(handler, null);
                else endpoints.put(handler, new MekanismRf.Endpoint(machine.relative(side), machine));
            }));
        }
    }

    @Override
    protected void acceptAmount(IStrictEnergyHandler handler, SplitInfo<FloatingLong> split, FloatingLong amount) {
        FloatingLong before = split.getTotalSent().copy();
        super.acceptAmount(handler, split, amount);
        MekanismRf.delivered(network, endpoints.get(handler), split.getTotalSent().subtract(before));
    }
}

package art.arcane.auram.mixin.mekanism;

import art.arcane.auram.rf.mekanism.MekanismCableOwner;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import mekanism.common.capabilities.energy.DynamicStrictEnergyHandler;
import mekanism.common.tile.transmitter.TileEntityUniversalCable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TileEntityUniversalCable.class, remap = false)
abstract class RfCableTileMixin {
    @ModifyExpressionValue(method = "<init>", at = @At(value = "NEW",
            target = "mekanism/common/capabilities/energy/DynamicStrictEnergyHandler"))
    private DynamicStrictEnergyHandler auram$bindCapability(DynamicStrictEnergyHandler handler) {
        ((MekanismCableOwner) handler).auram$bindCable((TileEntityUniversalCable) (Object) this);
        return handler;
    }
}

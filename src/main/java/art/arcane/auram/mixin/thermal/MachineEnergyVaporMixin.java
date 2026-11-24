package art.arcane.auram.mixin.thermal;

import art.arcane.auram.rf.RfInputOwner;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "cofh.thermal.lib.common.block.entity.AugmentableBlockEntity", remap = false)
abstract class MachineEnergyVaporMixin {
    @Inject(method = {"lambda$getEnergyCapability$6", "lambda$updateHandlers$3"}, at = @At("RETURN"))
    private void auram$bindEnergyStorage(CallbackInfoReturnable<Object> result) {
        if (result.getReturnValue() instanceof RfInputOwner storage) {
            storage.auram$setRfInputOwner((BlockEntity) (Object) this);
        }
    }
}

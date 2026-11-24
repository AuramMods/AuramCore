package art.arcane.auram.mixin.enderio;

import art.arcane.auram.vapor.EnderIoVapor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.enderio.enderio.content.machines.niard.NiardBlockEntity", remap = false)
abstract class VaporNiardMixin {
    @Inject(method = "tryPlaceFluid()V", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lcom/enderio/enderio/foundation/energy/PoweredMachineEnergyStorage;takeEnergy(I)I"))
    private void auram$emitAfterFluidPlacement(CallbackInfo callback) {
        EnderIoVapor.operated((BlockEntity) (Object) this);
    }
}

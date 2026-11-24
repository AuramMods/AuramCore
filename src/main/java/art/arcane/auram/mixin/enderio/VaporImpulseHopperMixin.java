package art.arcane.auram.mixin.enderio;

import art.arcane.auram.vapor.EnderIoVapor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.enderio.enderio.content.machines.impulse_hopper.ImpulseHopperBlockEntity", remap = false)
abstract class VaporImpulseHopperMixin {
    @Inject(method = "passItems()V", at = @At("TAIL"))
    private void auram$emitAfterBatch(CallbackInfo callback) {
        EnderIoVapor.operated((BlockEntity) (Object) this);
    }
}

package art.arcane.auram.mixin.enderio;

import art.arcane.auram.vapor.EnderIoVapor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.enderio.enderio.content.machines.drain.DrainBlockEntity", remap = false)
abstract class VaporDrainMixin {
    @Inject(method = "drainFluids()V", at = @At("HEAD"))
    private void auram$emitWhilePumping(CallbackInfo callback) {
        EnderIoVapor.operating((BlockEntity) (Object) this);
    }
}

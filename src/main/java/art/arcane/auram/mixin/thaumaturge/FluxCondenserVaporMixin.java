package art.arcane.auram.mixin.thaumaturge;

import art.arcane.auram.vapor.ThaumaturgeVapor;
import com.leclowndu93150.thaumaturge.content.device.BlockEntityCondenser;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockEntityCondenser.class, remap = false)
abstract class FluxCondenserVaporMixin {
    @Inject(method = "condense", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER,
            remap = false, target = "Lcom/leclowndu93150/thaumaturge/content/device/BlockEntityCondenser;flux:I"))
    private void auram$vapor(CallbackInfo callback) {
        ThaumaturgeVapor.machine((BlockEntityCondenser) (Object) this, 10);
    }
}

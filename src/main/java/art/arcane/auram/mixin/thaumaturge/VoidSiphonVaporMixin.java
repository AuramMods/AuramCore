package art.arcane.auram.mixin.thaumaturge;

import art.arcane.auram.vapor.ThaumaturgeVapor;
import com.leclowndu93150.thaumaturge.content.device.BlockEntityVoidSiphon;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockEntityVoidSiphon.class, remap = false)
abstract class VoidSiphonVaporMixin {
    @Inject(method = "serverTick", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER,
            remap = false, target = "Lcom/leclowndu93150/thaumaturge/content/device/BlockEntityVoidSiphon;progress:I"))
    private static void auram$vapor(Level level, BlockPos pos, BlockState state, BlockEntityVoidSiphon siphon, CallbackInfo callback) {
        ThaumaturgeVapor.machine(siphon, 10);
    }
}

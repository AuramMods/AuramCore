package art.arcane.auram.mixin.thaumaturge;

import art.arcane.auram.vapor.ThaumaturgeVapor;
import com.leclowndu93150.thaumaturge.content.device.bore.BlockEntityArcaneBore;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockEntityArcaneBore.class, remap = false)
abstract class ArcaneBoreVaporMixin {
    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void auram$vapor(Level level, BlockPos pos, BlockState state, BlockEntityArcaneBore machine,
                                    CallbackInfo callback) {
        ThaumaturgeVapor.machine(machine, machine.digging());
    }
}

package art.arcane.auram.mixin.thaumaturge;

import art.arcane.auram.vapor.ThaumaturgeVapor;
import com.leclowndu93150.thaumaturge.content.spell.manipulator.BlockEntityFocalManipulator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockEntityFocalManipulator.class, remap = false)
abstract class FocalManipulatorVaporMixin {
    @Inject(method = "tickServer", at = @At("RETURN"))
    private void auram$vapor(CallbackInfo callback) {
        var machine = (BlockEntityFocalManipulator) (Object) this;
        ThaumaturgeVapor.machine(machine, machine.inscribing());
    }
}

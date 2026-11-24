package art.arcane.auram.mixin.thaumaturge;

import art.arcane.auram.vapor.ThaumaturgeVapor;
import com.leclowndu93150.thaumaturge.content.infernalfurnace.BlockEntityInfernalFurnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockEntityInfernalFurnace.class, remap = false)
abstract class InfernalFurnaceVaporMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private void auram$vapor(CallbackInfo callback) {
        var machine = (BlockEntityInfernalFurnace) (Object) this;
        ThaumaturgeVapor.machine(machine, machine.furnaceCookTime > 0);
    }
}

package art.arcane.auram.mixin.thermal;

import art.arcane.auram.vapor.MachineVapor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "cofh.thermal.lib.common.block.entity.DynamoBlockEntity", remap = false)
abstract class DynamoVaporMixin {
    @Inject(method = "processTick", at = @At("RETURN"))
    private void auram$burningFuel(CallbackInfoReturnable<Integer> result) {
        if (result.getReturnValueI() > 0) MachineVapor.burningTaggedGenerator((BlockEntity) (Object) this);
    }
}

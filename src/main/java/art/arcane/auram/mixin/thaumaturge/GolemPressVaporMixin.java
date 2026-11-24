package art.arcane.auram.mixin.thaumaturge;

import art.arcane.auram.vapor.ThaumaturgeVapor;
import com.leclowndu93150.thaumaturge.content.golem.press.BlockEntityGolemBuilder;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(value = BlockEntityGolemBuilder.class, remap = false)
abstract class GolemPressVaporMixin {
    @Inject(method = "payUnit", at = @At("RETURN"))
    private void auram$vapor(CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) ThaumaturgeVapor.machine((BlockEntityGolemBuilder) (Object) this, 5);
    }
}

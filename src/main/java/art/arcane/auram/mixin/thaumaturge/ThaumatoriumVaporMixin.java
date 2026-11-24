package art.arcane.auram.mixin.thaumaturge;

import art.arcane.auram.vapor.ThaumaturgeVapor;
import com.leclowndu93150.thaumaturge.content.essentia.thaumatorium.BlockEntityThaumatorium;
import com.leclowndu93150.thaumaturge.content.essentia.thaumatorium.work.ThaumatoriumScheduler;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockEntityThaumatorium.class, remap = false)
abstract class ThaumatoriumVaporMixin {
    @Inject(method = "pullUnit", at = @At("RETURN"))
    private void auram$pullVapor(CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) {
            ThaumaturgeVapor.machine((BlockEntityThaumatorium) (Object) this, ThaumatoriumScheduler.WORK_TICKS);
        }
    }

    @Inject(method = "credit", at = @At("HEAD"))
    private void auram$craftVapor(CallbackInfo callback) {
        ThaumaturgeVapor.machine((BlockEntityThaumatorium) (Object) this, ThaumatoriumScheduler.WORK_TICKS);
    }
}

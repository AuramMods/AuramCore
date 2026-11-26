package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.WorldLoadCapture;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Pseudo
@Mixin(targets = "com.sk89q.worldedit.world.block.BlockState", remap = false)
abstract class WorldEditStateMapProbeMixin {
    @Inject(method = "generateStateMap", at = @At("HEAD"))
    private static void auram$beginMap(CallbackInfoReturnable<Map<?, ?>> cir, @Share("mapStart") LocalLongRef start) {
        if (WorldLoadCapture.WINDOW_NANOS > 0) start.set(System.nanoTime());
    }

    @Inject(method = "generateStateMap", at = @At("RETURN"))
    private static void auram$endMap(CallbackInfoReturnable<Map<?, ?>> cir, @Share("mapStart") LocalLongRef start) {
        if (start.get() != 0) WorldLoadCapture.worldEditStateMap(System.nanoTime() - start.get(), cir.getReturnValue().size());
    }

    @WrapMethod(method = "populate")
    private void auram$timeNeighborTable(Map<?, ?> states, Operation<Void> original) {
        if (WorldLoadCapture.WINDOW_NANOS <= 0) {
            original.call(states);
            return;
        }
        long start = System.nanoTime();
        try {
            original.call(states);
        } finally {
            WorldLoadCapture.worldEditNeighborTable(System.nanoTime() - start);
        }
    }
}

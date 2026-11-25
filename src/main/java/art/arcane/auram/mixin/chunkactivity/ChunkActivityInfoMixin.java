package art.arcane.auram.mixin.chunkactivity;

import art.arcane.auram.util.ChunkActivityTotal;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

@Pseudo
@Mixin(targets = "toni.chunkactivitytracker.data.ChunkActivityInfo", remap = false)
abstract class ChunkActivityInfoMixin implements ChunkActivityTotal {
    @Shadow @Final private ConcurrentHashMap<UUID, Long> playerTimeMap;
    @Unique private volatile long auram$total;
    @Unique private volatile boolean auram$mapExposed;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void auram$initializeTotal(CallbackInfo info) {
        auram$total = auram$sumTimes();
    }

    @Inject(method = "getPlayerTimeMap", at = @At("HEAD"))
    private void auram$allowExternalEdits(CallbackInfoReturnable<ConcurrentHashMap<UUID, Long>> result) {
        // The public API exposes a mutable map whose callers can bypass updatePlayerTime.
        auram$mapExposed = true;
    }

    @WrapOperation(method = "updatePlayerTime", at = @At(value = "INVOKE", target =
            "Ljava/util/concurrent/ConcurrentHashMap;merge(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"))
    private Object auram$recordTime(ConcurrentHashMap<?, ?> map, Object player, Object increment,
                                   BiFunction<?, ?, ?> merge, Operation<Object> original) {
        synchronized (this) {
            Object result = original.call(map, player, increment, merge);
            auram$total += (Long) increment;
            return result;
        }
    }

    @Override
    public long auram$totalActivity() {
        return auram$mapExposed ? auram$sumTimes() : auram$total;
    }

    @Unique
    private long auram$sumTimes() {
        long total = 0;
        for (long time : playerTimeMap.values()) total += time;
        return total;
    }
}

package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.AquiferReuseProbe;
import art.arcane.auram.smoke.WorldgenTargets;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.locks.StampedLock;

@Pseudo
@Mixin(targets = "terrablender.worldgen.noise.Area", remap = false)
public abstract class TerraBlenderLockProbeMixin {
    @WrapOperation(method = "get", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/StampedLock;readLock()J"))
    private long auramSmoke$read(StampedLock lock, Operation<Long> original) {
        return auramSmoke$acquire(lock, original, WorldgenTargets.Target.TERRA_READ_LOCK);
    }

    @WrapOperation(method = "get", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/StampedLock;writeLock()J"))
    private long auramSmoke$write(StampedLock lock, Operation<Long> original) {
        return auramSmoke$acquire(lock, original, WorldgenTargets.Target.TERRA_WRITE_LOCK);
    }

    @Unique
    private static long auramSmoke$acquire(StampedLock lock, Operation<Long> original, WorldgenTargets.Target target) {
        if (!WorldgenTargets.ENABLED || !AquiferReuseProbe.fillingBiomes()) return original.call(lock);
        long wall = System.nanoTime();
        long cpu = WorldgenTargets.cpu();
        long stamp = original.call(lock);
        WorldgenTargets.record(target, WorldgenTargets.cpu() - cpu, System.nanoTime() - wall);
        return stamp;
    }
}

package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.SurfaceMapBenchSmoke;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "art.arcane.auram.map.SurfaceTileStore", remap = false)
abstract class SurfaceMapCaptureProbeMixin {
    @Unique private final ThreadLocal<Long> auram$started = new ThreadLocal<>();

    @Inject(method = "capture", at = @At("HEAD"))
    private void auram$start(ServerLevel level, ChunkAccess chunk, boolean refresh, CallbackInfoReturnable<Boolean> cir) {
        if (SurfaceMapBenchSmoke.ENABLED) {
            auram$started.set(System.nanoTime());
        }
    }

    @Inject(method = "capture", at = @At("RETURN"))
    private void auram$finish(ServerLevel level, ChunkAccess chunk, boolean refresh, CallbackInfoReturnable<Boolean> cir) {
        if (SurfaceMapBenchSmoke.ENABLED) {
            SurfaceMapBenchSmoke.capture(System.nanoTime() - auram$started.get(), level.getServer().isSameThread());
            auram$started.remove();
        }
    }
}

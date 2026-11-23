package art.arcane.auram.mixin.bettercaves;

import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ConcurrentHashMap;

@Pseudo
@Mixin(targets = "com.yungnickyoung.minecraft.bettercaves.worldgen.liquidregion.LiquidRegions", remap = false)
abstract class LiquidRegionsCacheMixin {
    @Shadow @Final public ConcurrentHashMap<ChunkPos, ?> cache;

    @Inject(method = "getOrCreateLiquidBlocksForChunk", at = @At("HEAD"), cancellable = true)
    private void auram$readCachedLiquids(ChunkPos pos, CallbackInfoReturnable<Object> cir) {
        Object cached = cache.get(pos);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }
}

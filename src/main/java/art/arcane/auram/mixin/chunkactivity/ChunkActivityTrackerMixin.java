package art.arcane.auram.mixin.chunkactivity;

import art.arcane.auram.util.ChunkActivityTotal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Pseudo
@Mixin(targets = "toni.chunkactivitytracker.ChunkActivityTracker", remap = false)
abstract class ChunkActivityTrackerMixin {
    @Inject(method = "getTotalTimeInChunk", at = @At(value = "INVOKE", target =
            "Ltoni/chunkactivitytracker/data/ChunkActivityInfo;getPlayerTimeMap()Ljava/util/concurrent/ConcurrentHashMap;"),
            locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private static void auram$readTotal(Level level, ChunkPos pos, CallbackInfoReturnable<Long> result,
                                        @Coerce Object entry, long sum) {
        if (entry.getClass().getName().equals("toni.chunkactivitytracker.data.ChunkActivityInfo")
                && entry instanceof ChunkActivityTotal total) {
            result.setReturnValue(total.auram$totalActivity());
        }
    }
}

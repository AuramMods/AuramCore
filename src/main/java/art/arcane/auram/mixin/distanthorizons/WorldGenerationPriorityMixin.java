package art.arcane.auram.mixin.distanthorizons;

import art.arcane.auram.util.DistantGenerationPriority;
import art.arcane.auram.util.ForegroundChunkDemand;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.generation.WorldGenerationQueue", remap = false)
public abstract class WorldGenerationPriorityMixin {
    @Shadow public abstract int getWaitingTaskCount();
    @Shadow public abstract int getInProgressTaskCount();

    @Unique private final DistantGenerationPriority auram$priority = new DistantGenerationPriority();
    @Unique private ForegroundChunkDemand auram$chunkDemand;
    @Unique private Method auram$readMode;
    @Unique private Object auram$modeEntry;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void auram$bindDimension(@Coerce Object worldGenerator, @Coerce Object dhLevel, CallbackInfo ci) {
        try {
            Object wrapper = dhLevel.getClass().getMethod("getServerLevelWrapper").invoke(dhLevel);
            Object level = wrapper.getClass().getMethod("getWrappedMcObject").invoke(wrapper);
            if (!(level instanceof ServerLevel serverLevel) || !serverLevel.getServer().isDedicatedServer()) return;

            Class<?> config = Class.forName("com.seibel.distanthorizons.core.config.Config$Common$WorldGenerator",
                    false, getClass().getClassLoader());
            Object modeEntry = config.getField("distantGeneratorMode").get(null);
            if (modeEntry == null) return;
            auram$readMode = modeEntry.getClass().getMethod("get");
            auram$modeEntry = modeEntry;
            if (serverLevel.getChunkSource().chunkMap instanceof ForegroundChunkDemand demand) {
                auram$chunkDemand = demand;
            }
        } catch (ReflectiveOperationException ignored) {
            // An unsupported DH version retains its original generation queue.
        }
    }

    @Inject(method = "isGeneratorBusy", at = @At("RETURN"), cancellable = true)
    private void auram$prioritizePlayerChunks(CallbackInfoReturnable<Boolean> ci) {
        if (auram$chunkDemand == null || getWaitingTaskCount() == 0) return;
        try {
            Object mode = auram$readMode.invoke(auram$modeEntry);
            if (mode instanceof Enum<?> value && value.name().equals("INTERNAL_SERVER")) {
                boolean pending = auram$chunkDemand.auram$samplePlayerChunkDemand();
                if (!ci.getReturnValueZ() && auram$priority.shouldDefer(pending, getInProgressTaskCount(), System.nanoTime())) {
                    ci.setReturnValue(true);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            auram$chunkDemand = null;
        }
    }
}

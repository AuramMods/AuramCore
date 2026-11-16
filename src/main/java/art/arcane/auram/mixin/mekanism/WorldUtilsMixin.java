package art.arcane.auram.mixin.mekanism;

import art.arcane.auram.util.DeferredChunkDirty;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mekanism.common.util.WorldUtils", remap = false)
public abstract class WorldUtilsMixin {
    @Inject(method = "markChunkDirty", at = @At("HEAD"), cancellable = true, remap = false)
    private static void auram$markLoadedChunkDirty(Level level, BlockPos pos, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel) {
            DeferredChunkDirty.mark(serverLevel, pos);
            ci.cancel();
        }
    }
}

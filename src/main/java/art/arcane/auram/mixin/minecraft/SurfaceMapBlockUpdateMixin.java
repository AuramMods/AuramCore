package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.map.SurfaceMaps;
import art.arcane.auram.vapor.VaporDemonstrators;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class SurfaceMapBlockUpdateMixin {
    @Inject(method = "sendBlockUpdated", at = @At("TAIL"))
    private void auram$recordSurfaceChange(BlockPos pos, BlockState before, BlockState after, int flags, CallbackInfo ci) {
        if (before != after) {
            ServerLevel level = (ServerLevel) (Object) this;
            SurfaceMaps.blockChanged(level, pos);
            VaporDemonstrators.blockChanged(level, pos, before, after);
        }
    }
}

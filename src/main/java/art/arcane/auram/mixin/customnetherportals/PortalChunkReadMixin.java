package art.arcane.auram.mixin.customnetherportals;

import art.arcane.auram.util.PortalChunkPreparation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerChunkCache.class)
abstract class PortalChunkReadMixin {
    @Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
    private void auram$readPreparedPortalChunk(int x, int z, ChunkStatus status, boolean create,
                                                CallbackInfoReturnable<ChunkAccess> ci) {
        if (status != ChunkStatus.FULL || !create) return;
        PortalChunkPreparation lookup = PortalChunkPreparation.active();
        if (lookup != null && lookup.isDestination((ServerChunkCache) (Object) this)) {
            ci.setReturnValue(lookup.readChunk(x, z));
        }
    }
}

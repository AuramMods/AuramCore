package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.ChunkCacheProbe;
import art.arcane.auram.smoke.PatchSmokeMod;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerChunkCache.class)
public abstract class ChunkCacheMixin implements ChunkCacheProbe {
    @Invoker("runDistanceManagerUpdates")
    public abstract boolean auramSmoke$runDistanceManagerUpdates();

    @Inject(method = "getChunk", at = @At("HEAD"))
    private void auramSmoke$rejectBlockingLookup(int x, int z, ChunkStatus status, boolean create,
                                                CallbackInfoReturnable<ChunkAccess> ci) {
        if (PatchSmokeMod.forbiddenChunkLookups.contains(ChunkPos.asLong(x, z))) {
            throw new AssertionError("Patched hook requested a blocking chunk lookup at " + x + "," + z);
        }
    }
}

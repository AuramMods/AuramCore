package art.arcane.auram.mixin.ftbessentials;

import art.arcane.auram.util.FtbRtpSearch;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.function.BooleanSupplier;

@Mixin(ServerChunkCache.class)
public abstract class RtpBiomeWaitMixin {
    @WrapOperation(method = "getChunk", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerChunkCache$MainThreadExecutor;managedBlock(Ljava/util/function/BooleanSupplier;)V"))
    private void auram$deferRtpBiomeRead(@Coerce Object executor, BooleanSupplier ready, Operation<Void> original,
                                        int x, int z, ChunkStatus status, boolean create) {
        if (status == ChunkStatus.BIOMES && !create) FtbRtpSearch.yieldPendingBiome(ready);
        original.call(executor, ready);
    }
}

package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.map.client.FtbChunksSurface;
import dev.ftb.mods.ftbchunks.client.map.ChunkUpdateTask;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkUpdateTask.class, remap = false)
abstract class ChunkUpdateSurfaceMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private ChunkPos chunkPos;

    @Inject(method = "runMapTask", at = @At("HEAD"), cancellable = true)
    private void auram$skipCoveredTerrain(CallbackInfo ci) {
        if (FtbChunksSurface.covered(level.dimension().location(), chunkPos.x, chunkPos.z)) {
            ci.cancel();
        }
    }
}

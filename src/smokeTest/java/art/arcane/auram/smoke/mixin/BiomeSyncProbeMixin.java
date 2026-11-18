package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.BiomeFillProbe;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChunkMap.class)
public abstract class BiomeSyncProbeMixin {
    @Inject(method = "resendBiomesForChunks", at = @At("HEAD"))
    private void auramSmoke$captureRefresh(List<ChunkAccess> chunks, CallbackInfo ci) { BiomeFillProbe.refresh(chunks); }
}

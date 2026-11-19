package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.DistantHorizonsSmoke;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class DhDeliveryProbeMixin {
    @Inject(method = "trackChunk", at = @At("RETURN"))
    private void auram$chunkSent(ChunkPos pos, Packet<?> packet, CallbackInfo ci) {
        if (DistantHorizonsSmoke.SERVER) DistantHorizonsSmoke.delivered((ServerPlayer) (Object) this, pos);
    }
}

package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.DistantHorizonsSmoke;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DistanceManager.class)
public abstract class DhTicketProbeMixin {
    @Inject(method = "addTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V", at = @At("RETURN"))
    private <T> void auram$ticketAdded(TicketType<T> type, ChunkPos pos, int level, T value, CallbackInfo ci) {
        if (DistantHorizonsSmoke.SERVER && "dh_server_gen_ticket".equals(type.toString())) {
            DistantHorizonsSmoke.addTicket(pos);
        }
    }

    @Inject(method = "removeTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V", at = @At("RETURN"))
    private <T> void auram$ticketRemoved(TicketType<T> type, ChunkPos pos, int level, T value, CallbackInfo ci) {
        if (DistantHorizonsSmoke.SERVER && "dh_server_gen_ticket".equals(type.toString())) {
            DistantHorizonsSmoke.removeTicket(pos);
        }
    }
}

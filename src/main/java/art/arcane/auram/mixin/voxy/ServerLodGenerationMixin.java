package art.arcane.auram.mixin.voxy;

import art.arcane.auram.compat.voxy.LandingChunkDelivery;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "me.cortex.voxy.server.ServerLodService", remap = false)
public abstract class ServerLodGenerationMixin {
    @WrapOperation(method = "onTick", at = @At(value = "INVOKE",
            target = "Lme/cortex/voxy/server/ServerLodGenerator;tick(ZI)V"))
    private static void auram$prioritizeLandingChunks(@Coerce Object generator, boolean playersOnline, int jobs,
                                                     Operation<Void> tick, TickEvent.ServerTickEvent event) {
        if (jobs > 0) {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                if (player.connection instanceof LandingChunkDelivery delivery && delivery.auram$landingChunksPending()) {
                    // A zero job budget still advances active jobs and releases tickets.
                    jobs = 0;
                    break;
                }
            }
        }
        tick.call(generator, playersOnline, jobs);
    }
}

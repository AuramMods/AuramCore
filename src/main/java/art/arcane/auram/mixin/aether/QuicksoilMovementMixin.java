package art.arcane.auram.mixin.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.zepalesque.redux.event.listener.MobListener", remap = false)
public abstract class QuicksoilMovementMixin {
    @Inject(method = "movementHandling", at = @At("HEAD"), cancellable = true, remap = false)
    private static void auram$avoidQuicksoilChunkLoads(LivingEvent.LivingTickEvent event, CallbackInfo ci) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        // getBlockPosBelowThatAffectsMyMovement() reads the supporting block before
        // Redux's isLoaded() check. In 1.20.1 getOnPos() returns that same X/Z
        // without reading terrain, including support in an adjacent chunk.
        if (!auram$isLoaded(level, entity.getOnPos())) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean auram$isLoaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ())
        ) != null;
    }
}

package art.arcane.auram.mixin.malum;

import art.arcane.auram.util.WellProximity;
import com.sammy.malum.core.handlers.TouchOfDarknessHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

@Pseudo
@Mixin(targets = "com.sammy.malum.core.handlers.TouchOfDarknessHandler", remap = false)
public abstract class WellProximityMixin {
    @Redirect(method = "entityTick", at = @At(value = "INVOKE", target =
            "Lcom/sammy/malum/core/handlers/TouchOfDarknessHandler;checkForWeepingWell(Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Optional;"), remap = false)
    private static Optional<?> auram$deferPeriodicLookup(LivingEntity entity) {
        // This Optional only feeds the assignment below. Replace the pair as a unit
        // so an incomplete scan can leave the previous boolean intact. Other uses
        // of checkForWeepingWell (notably rejection) keep the original method.
        return entity.level() instanceof ServerLevel ? Optional.empty() : TouchOfDarknessHandler.checkForWeepingWell(entity);
    }

    @Redirect(method = "entityTick", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target =
            "Lcom/sammy/malum/core/handlers/TouchOfDarknessHandler;isNearWeepingWell:Z"), remap = false)
    private static void auram$updateReadyProximity(TouchOfDarknessHandler handler, boolean near,
                                                 LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            Boolean result = WellProximity.find(level, event.getEntity().blockPosition());
            if (result != null) handler.isNearWeepingWell = result;
        } else {
            handler.isNearWeepingWell = near;
        }
    }
}

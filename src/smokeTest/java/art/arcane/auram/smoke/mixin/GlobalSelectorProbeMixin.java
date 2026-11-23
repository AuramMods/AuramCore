package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.SelectorProbe;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

@Mixin(EntitySelector.class)
abstract class GlobalSelectorProbeMixin {
    @Inject(method = "addEntities", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerLevel;getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Ljava/util/function/Predicate;Ljava/util/List;I)V"))
    private void auramSmoke$countGlobalSelectors(List<Entity> results, ServerLevel level, Vec3 position,
                                                   Predicate<Entity> predicate, CallbackInfo ci) {
        SelectorProbe.recordGlobalScan();
    }
}

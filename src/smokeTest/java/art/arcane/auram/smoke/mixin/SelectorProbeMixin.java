package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.SelectorProbe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class SelectorProbeMixin {
    @Inject(method="saveWithoutId",at=@At("HEAD"))
    private void auramSmoke$serialize(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        SelectorProbe.record();
    }
}

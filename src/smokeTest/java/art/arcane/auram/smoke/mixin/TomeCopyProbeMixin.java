package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.TomeHudSmoke;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CompoundTag.class)
public abstract class TomeCopyProbeMixin {
    @Inject(method = "copy()Lnet/minecraft/nbt/CompoundTag;", at = @At("HEAD"))
    private void auramSmoke$countFullTomeCopies(CallbackInfoReturnable<CompoundTag> ci) {
        TomeHudSmoke.observeCopy((CompoundTag) (Object) this);
    }
}

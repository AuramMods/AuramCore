package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.ComparisonProbe;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CapabilityProvider.class, remap = false)
public abstract class CapabilityComparisonProbeMixin {
    @Inject(method = "areCapsCompatible(Lnet/minecraftforge/common/capabilities/CapabilityProvider;)Z", at = @At("HEAD"), cancellable = true)
    private void auramSmoke$count(CapabilityProvider<?> other, CallbackInfoReturnable<Boolean> cir) {
        ComparisonProbe.record(this, other);
        // Experimental candidate stays in the test mod. The release jar does not
        // include it: the copied-snapshot workload has no identity hits.
        if (Boolean.getBoolean("auram.smoke.identityCandidate") && (Object) this == other
                && (Object) this instanceof ItemStack) cir.setReturnValue(true);
    }
}

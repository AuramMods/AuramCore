package art.arcane.auram.mixin.malum;

import art.arcane.auram.util.WellFlatness;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.sammy.malum.common.worldgen.WeepingWellStructure", remap = false)
public abstract class WeepingWellMixin {
    @Inject(method = "isSufficientlyFlat", at = @At("HEAD"), cancellable = true, remap = false)
    private void auram$stopDeterminedFlatnessScan(Structure.GenerationContext context, BlockPos center, int radius,
                                                 CallbackInfoReturnable<Boolean> ci) {
        // Larger positive radii overflow the original int-sized scan/threshold;
        // leave that unsupported input's behavior to the original implementation.
        if (radius <= 23170) ci.setReturnValue(WellFlatness.test(context, center, radius));
    }
}

package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.ColumnProbe;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.NoiseColumn;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseColumnProbeMixin {
    @Inject(method = "getBaseColumn", at = @At("HEAD"))
    private void auramSmoke$countColumns(CallbackInfoReturnable<NoiseColumn> ci) {
        long[] calls = ColumnProbe.CALLS.get();
        if (calls[0] >= 0) calls[0]++;
    }
}

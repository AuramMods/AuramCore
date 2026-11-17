package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.ColumnProbe;
import art.arcane.auram.smoke.TerrainQueryProbe;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseChunk.class)
public abstract class NoiseSetupProbeMixin {
    @Inject(method="<init>",at=@At("RETURN"))
    private void auramSmoke$countSetup(CallbackInfo ci) {
        if(ColumnProbe.NOISE.get()[0]>=0) ColumnProbe.NOISE.get()[0]++;
        TerrainQueryProbe.recordSetup();
    }
}

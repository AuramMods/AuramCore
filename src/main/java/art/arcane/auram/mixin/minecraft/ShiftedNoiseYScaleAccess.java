package art.arcane.auram.mixin.minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$ShiftedNoise")
public interface ShiftedNoiseYScaleAccess {
    @Accessor("yScale")
    double auram$yScale();
}

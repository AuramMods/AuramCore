package art.arcane.auram.mixin.minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Noise")
public interface NoiseYScaleAccess {
    @Accessor("yScale")
    double auram$yScale();
}

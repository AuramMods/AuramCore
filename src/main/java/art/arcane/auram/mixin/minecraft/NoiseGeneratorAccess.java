package art.arcane.auram.mixin.minecraft;

import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseGeneratorAccess {
    @Accessor("globalFluidPicker") Supplier<Aquifer.FluidPicker> auram$fluidPicker();
}

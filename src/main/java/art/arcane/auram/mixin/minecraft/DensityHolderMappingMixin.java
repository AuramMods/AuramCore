package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.util.NoiseMappingScope;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DensityFunctions.HolderHolder.class)
public abstract class DensityHolderMappingMixin {
    @Redirect(method="mapAll",at=@At(value="INVOKE",target=
            "Lnet/minecraft/world/level/levelgen/DensityFunction;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/DensityFunction;"))
    private DensityFunction auram$mapSharedInput(DensityFunction function,DensityFunction.Visitor visitor) {
        return NoiseMappingScope.shared(function,visitor);
    }
}

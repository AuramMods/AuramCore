package art.arcane.auram.mixin.terrablender;

import art.arcane.auram.util.TerraBlenderRegions;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "terrablender.worldgen.noise.Area", remap = false)
public abstract class RegionAreaMixin {
    @WrapMethod(method = "get(II)I")
    private int auram$reusePositionalRegion(int x, int z, Operation<Integer> original) {
        return TerraBlenderRegions.region(this, x, z, () -> original.call(x, z));
    }
}

package art.arcane.auram.mixin.minecraft;

import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BiomeManager.class)
public interface BiomeManagerAccess {
    @Accessor("biomeZoomSeed")
    long auram$biomeZoomSeed();

    @Accessor("noiseBiomeSource")
    BiomeManager.NoiseBiomeSource auram$noiseBiomeSource();
}

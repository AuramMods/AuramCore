package art.arcane.auram.mixin.immersiveweathering;

import art.arcane.auram.util.NaturalSnow;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

@Pseudo
@Mixin(targets = "com.ordana.immersive_weathering.util.TemperatureManager", remap = false)
abstract class SnowGrowthMixin {
    @ModifyReturnValue(method = "snowGrowthCanGrowSnowyBlock", at = @At("RETURN"))
    private static boolean auram$requireNaturalSnowCover(boolean allowed, BlockPos pos, Supplier<Holder<Biome>> biome) {
        return allowed && NaturalSnow.canAccumulate(biome.get(), pos);
    }
}

package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.util.NaturalSnow;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
abstract class SnowAccumulationMixin {
    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean auram$requireNaturalSnowCover(Biome biome, LevelReader level, BlockPos pos,
                                                  Operation<Boolean> original) {
        return NaturalSnow.canAccumulate(level.getBiome(pos), pos) && original.call(biome, level, pos);
    }
}

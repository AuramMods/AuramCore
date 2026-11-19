package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.LithoStartProbe;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.worldgen.lithostitched.worldgen.structure.AlternateJigsawGenerator", remap = false)
public abstract class LithoStartProjectionProbeMixin {
    @WrapOperation(method = "lambda$generate$1", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/chunk/ChunkGenerator;getFirstFreeHeight(IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)I", remap = true))
    private static int auramSmoke$trackProjection(ChunkGenerator generator, int x, int z, Heightmap.Types type,
                                                  LevelHeightAccessor height, RandomState random, Operation<Integer> original) {
        LithoStartProbe.projection(generator, x, z, type, height, random);
        return original.call(generator, x, z, type, height, random);
    }
}

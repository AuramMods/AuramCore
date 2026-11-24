package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.util.EmptyBeardifier;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class EmptyBeardifierMixin {
    @ModifyArg(method = "createNoiseChunk", index = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseChunk;forChunk(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/DensityFunctions$BeardifierOrMarker;Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;Lnet/minecraft/world/level/levelgen/blending/Blender;)Lnet/minecraft/world/level/levelgen/NoiseChunk;"))
    private DensityFunctions.BeardifierOrMarker auram$simplifyEmptyBeardifier(ChunkAccess chunk,
            RandomState random, DensityFunctions.BeardifierOrMarker beardifier, NoiseGeneratorSettings settings,
            Aquifer.FluidPicker fluidPicker, Blender blender) {
        return blender == Blender.empty() ? EmptyBeardifier.simplify(beardifier) : beardifier;
    }
}

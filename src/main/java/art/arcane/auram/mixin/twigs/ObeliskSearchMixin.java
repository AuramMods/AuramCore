package art.arcane.auram.mixin.twigs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Pseudo
@Mixin(targets = "com.ninni.twigs.world.gen.structures.ObeliskStructure", remap = false)
public abstract class ObeliskSearchMixin {
    @Inject(method = "canGenerate", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/chunk/ChunkGenerator;getBaseColumn(IILnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Lnet/minecraft/world/level/NoiseColumn;",
            remap = true), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private static void auram$finishObeliskSearch(Structure.GenerationContext context, BlockPos pos,
                                                 CallbackInfoReturnable<Boolean> cir, boolean found) {
        // Twigs never clears the match flag. Further vanilla probes cannot
        // change the answer, while a custom generator can observe those calls.
        if (found && context.chunkGenerator().getClass() == NoiseBasedChunkGenerator.class) {
            cir.setReturnValue(true);
        }
    }
}

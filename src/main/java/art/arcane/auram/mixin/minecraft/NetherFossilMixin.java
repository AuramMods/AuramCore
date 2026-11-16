package art.arcane.auram.mixin.minecraft;

import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.NetherFossilStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Optional;

@Mixin(NetherFossilStructure.class)
public abstract class NetherFossilMixin {
    @Inject(method = "findGenerationPoint", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/chunk/ChunkGenerator;getBaseColumn(IILnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Lnet/minecraft/world/level/NoiseColumn;"),
            cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private void auram$rejectBelowSea(Structure.GenerationContext context,
                                     CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir,
                                     WorldgenRandom random, int x, int z, int seaLevel,
                                     WorldGenerationContext worldgenContext, int sampledHeight) {
        // Vanilla's search only decreases this height and rejects <= sea level.
        // All original RNG draws have already occurred; no column can change the
        // outcome here. Higher candidates retain the complete original search.
        if (sampledHeight <= seaLevel) cir.setReturnValue(Optional.empty());
    }
}

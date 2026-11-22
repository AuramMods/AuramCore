package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.util.DeferredRingSearch;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConcentricRingsStructurePlacement.class)
public abstract class ConcentricPlacementMixin {
    @Inject(method = "isPlacementChunk", at = @At("HEAD"), cancellable = true)
    private void auram$checkNearbyCandidates(ChunkGeneratorStructureState state, int x, int z,
                                             CallbackInfoReturnable<Boolean> callback) {
        var placement = (ConcentricRingsStructurePlacement) (Object) this;
        Boolean present = ((DeferredRingSearch.Access) state).auram$containsRingChunk(placement, x, z);
        if (present != null) callback.setReturnValue(present);
    }
}

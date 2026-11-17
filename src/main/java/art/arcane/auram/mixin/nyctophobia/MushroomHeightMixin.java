package art.arcane.auram.mixin.nyctophobia;

import art.arcane.auram.util.TerrainCell;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="net.finallion.nyctophobia.world.structures.MushroomStructure",remap=false)
public abstract class MushroomHeightMixin {
    @Inject(method="extraSpawningChecks",at=@At("HEAD"),cancellable=true)
    private static void auram$testHeightThreshold(Structure.GenerationContext context,CallbackInfoReturnable<Boolean> cir) {
        if(context.chunkGenerator().getClass()!=NoiseBasedChunkGenerator.class) return;
        int x=context.chunkPos().getMinBlockX(),z=context.chunkPos().getMinBlockZ();
        try(TerrainCell cell=TerrainCell.open((NoiseBasedChunkGenerator)context.chunkGenerator(),x,z,
                context.heightAccessor(),context.randomState())) {
            // The mod tests getFirstOccupiedHeight > 64. Base/free height is
            // one higher, so it must exceed 65 (an opaque block at Y=65).
            if(cell!=null) cir.setReturnValue(cell.heightExceeds(x,z,Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    65,context.heightAccessor().getMinBuildHeight()));
        }
    }
}

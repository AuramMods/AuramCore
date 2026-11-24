package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.util.TerrainBatch;
import art.arcane.auram.util.TerrainCell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.NetherFossilStructure;
import net.minecraft.world.level.levelgen.structure.structures.NetherFossilPieces;
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
        // outcome here. All supported higher candidates retain the same search,
        // sampling its states on demand instead of constructing a full column.
        if (sampledHeight <= seaLevel) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        if(context.chunkGenerator().getClass()!=NoiseBasedChunkGenerator.class) return;
        var generator = (NoiseBasedChunkGenerator) context.chunkGenerator();
        TerrainBatch batch = TerrainBatch.current(generator, context.heightAccessor(), context.randomState());
        TerrainCell shared = batch == null ? null : batch.borrowCell(x, z, sampledHeight);
        try (TerrainCell owned = batch == null
                ? TerrainCell.open(generator, x, z, context.heightAccessor(), context.randomState(), sampledHeight) : null) {
            TerrainCell cell = shared == null ? owned : shared;
            if(cell==null) return;
            // All states above maxY are air in the original NoiseColumn.
            int y=Math.min(sampledHeight,cell.maxY());
            BlockPos.MutableBlockPos position=new BlockPos.MutableBlockPos(x,y,z);
            BlockState above=cell.state(x,y,z);
            while(y>seaLevel) {
                BlockState floor=cell.state(x,--y,z);
                if(above.isAir() && (floor.is(Blocks.SOUL_SAND)
                        || floor.isFaceSturdy(EmptyBlockGetter.INSTANCE,position.setY(y),Direction.UP))) break;
                above=floor;
            }
            if(y<=seaLevel) cir.setReturnValue(Optional.empty());
            else {
                BlockPos found=new BlockPos(x,y,z);
                cir.setReturnValue(Optional.of(new Structure.GenerationStub(found,builder ->
                        NetherFossilPieces.addPieces(context.structureTemplateManager(),builder,random,found))));
            }
        }
    }
}

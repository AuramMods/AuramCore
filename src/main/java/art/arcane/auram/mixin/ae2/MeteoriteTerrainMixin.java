package art.arcane.auram.mixin.ae2;

import art.arcane.auram.util.TerrainBatch;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets="appeng.worldgen.meteorite.MeteoriteStructure",remap=false)
public abstract class MeteoriteTerrainMixin {
    @Shadow(remap=false)
    private static void generatePieces(StructurePiecesBuilder builder,Structure.GenerationContext context) { throw new AssertionError(); }

    @Redirect(method="lambda$findGenerationPoint$0",at=@At(value="INVOKE",target=
            "Lappeng/worldgen/meteorite/MeteoriteStructure;generatePieces(Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePiecesBuilder;Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;)V"))
    private static void auram$batchMeteorite(StructurePiecesBuilder builder,Structure.GenerationContext context) {
        TerrainBatch.scoped(context.chunkGenerator(),context.heightAccessor(),context.randomState(),true,()->{
            generatePieces(builder,context);return null;
        });
    }

    @Redirect(method={"generatePieces","locateWaterAroundTheCrater"},at=@At(value="INVOKE",target=
            "Lnet/minecraft/world/level/chunk/ChunkGenerator;getBaseHeight(IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)I",remap=true),require=2)
    private static int auram$batchedHeight(ChunkGenerator generator,int x,int z,Heightmap.Types type,LevelHeightAccessor height,RandomState state) {
        return TerrainBatch.queryHeight(generator,x,z,type,height,state);
    }
}

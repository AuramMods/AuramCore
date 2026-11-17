package art.arcane.auram.mixin.mowziesmobs;

import art.arcane.auram.util.StructureColumns;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets="com.bobmowzie.mowziesmobs.server.world.feature.structure.WroughtnautChamberStructure",remap=false)
public abstract class WroughtnautColumnsMixin {
    @Shadow(remap=false)
    public static Pair<BlockPos, Rotation> tryWroughtChamber(ChunkGenerator generator, LevelHeightAccessor height,
                                                           int x, int y, int z, RandomState state) {
        throw new AssertionError();
    }

    @Redirect(method="generatePieces",at=@At(value="INVOKE",target=
            "Lcom/bobmowzie/mowziesmobs/server/world/feature/structure/WroughtnautChamberStructure;tryWroughtChamber(Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/LevelHeightAccessor;IIILnet/minecraft/world/level/levelgen/RandomState;)Lorg/apache/commons/lang3/tuple/Pair;"))
    private Pair<BlockPos, Rotation> auram$searchColumns(ChunkGenerator generator, LevelHeightAccessor height,
                                                        int x, int y, int z, RandomState state) {
        return StructureColumns.search(generator,height,state,()->tryWroughtChamber(generator,height,x,y,z,state));
    }

    @Redirect(method="tryWroughtChamber",at=@At(value="INVOKE",target=
            "Lnet/minecraft/world/level/chunk/ChunkGenerator;getBaseColumn(IILnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Lnet/minecraft/world/level/NoiseColumn;",remap=true),require=2)
    private static NoiseColumn auram$reuseColumn(ChunkGenerator generator,int x,int z,LevelHeightAccessor height,RandomState state) {
        return StructureColumns.column(generator,x,z,height,state);
    }
}

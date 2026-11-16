package art.arcane.auram.mixin.lithostitched;

import art.arcane.auram.util.StructureHeightCache;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets="dev.worldgen.lithostitched.worldgen.structure.AlternateJigsawGenerator$StructurePoolGenerator",remap=false)
public abstract class StructurePoolGeneratorMixin {
    @Unique private final StructureHeightCache auram$heights = new StructureHeightCache();

    @Redirect(method="findValidChildPiece",at=@At(value="INVOKE",target=
            "Lnet/minecraft/world/level/chunk/ChunkGenerator;getFirstFreeHeight(IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)I",remap=true),require=2)
    private int auram$reuseHeight(ChunkGenerator generator,int x,int z,Heightmap.Types type,
                                  LevelHeightAccessor height,RandomState state) {
        return auram$heights.firstFree(generator,x,z,type,height,state);
    }
}

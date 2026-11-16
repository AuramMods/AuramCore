package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.ColumnProbe;
import art.arcane.auram.smoke.TerrainQueryProbe;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.NoiseColumn;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseColumnProbeMixin {
    @Inject(method = "getBaseColumn", at = @At("HEAD"))
    private void auramSmoke$countColumns(int x, int z, LevelHeightAccessor height, RandomState state, CallbackInfoReturnable<NoiseColumn> ci) {
        long[] calls = ColumnProbe.CALLS.get();
        if (calls[0] >= 0) calls[0]++;
        TerrainQueryProbe.record(this,x,z,height,state,null);
    }

    @Inject(method = "getBaseHeight", at = @At("HEAD"))
    private void auramSmoke$countHeights(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState state, CallbackInfoReturnable<Integer> ci) {
        TerrainQueryProbe.record(this,x,z,height,state,type);
    }
}

package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.ColumnProbe;
import art.arcane.auram.smoke.LithoStartProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "dev.worldgen.lithostitched.worldgen.structure.AlternateJigsawGenerator", remap = false)
public abstract class LithoStartProjectionProbeMixin {
    // Trace the whole helper so the probe surrounds both vanilla and patched
    // height reads regardless of the order of call-site mixins.
    @WrapMethod(method = "lambda$generate$1", remap = false)
    private static Integer auramSmoke$trackProjection(BlockPos pos, ChunkGenerator generator, int x, int z,
                                                      LevelHeightAccessor height, Structure.GenerationContext context,
                                                      Heightmap.Types type, Operation<Integer> original) {
        if (!LithoStartProbe.projection(generator, x, z, type, height, context.randomState())) {
            return original.call(pos, generator, x, z, height, context, type);
        }
        ColumnProbe.start();
        long cpu = LithoStartProbe.cpuTime();
        long started = System.nanoTime();
        int projected;
        try {
            projected = original.call(pos, generator, x, z, height, context, type);
        } finally {
            ColumnProbe.stop();
            LithoStartProbe.sampled(cpu, started, ColumnProbe.lastNoise());
        }
        if (Boolean.getBoolean("auram.smoke.verifyLithoStarts")) {
            int reference = pos.getY() + generator.getFirstFreeHeight(x, z, type, height, context.randomState());
            if (projected != reference) {
                throw new AssertionError("Lithostitched projection differs at " + x + "," + z + " " + type
                        + " reference=" + reference + " candidate=" + projected);
            }
        }
        return projected;
    }
}

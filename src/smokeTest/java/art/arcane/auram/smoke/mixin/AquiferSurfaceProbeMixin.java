package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.AquiferReuseProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(NoiseChunk.class)
public abstract class AquiferSurfaceProbeMixin {
    @Shadow @Final private Long2IntMap preliminarySurfaceLevel;
    @Shadow @Final private NoiseSettings noiseSettings;
    @Shadow @Final private Blender blender;
    @Shadow @Final private DensityFunction initialDensityNoJaggedness;

    @WrapMethod(method = "preliminarySurfaceLevel")
    private int auramSmoke$recordSurfaceScan(int x, int z, Operation<Integer> original) {
        if (!AquiferReuseProbe.ENABLED) return original.call(x, z);
        var scope = AquiferReuseProbe.current();
        if (scope == null) return original.call(x, z);
        long column = ColumnPos.asLong(QuartPos.toBlock(QuartPos.fromBlock(x)), QuartPos.toBlock(QuartPos.fromBlock(z)));
        long start = AquiferReuseProbe.beforeScan(scope, preliminarySurfaceLevel.containsKey(column));
        int result = original.call(x, z);
        AquiferReuseProbe.afterScan(scope, start, blender == Blender.empty(), noiseSettings, column, result);
        if (AquiferReuseProbe.VERIFY_HEIGHTS && start >= 0) {
            int expected = auramSmoke$uncachedHeight(ColumnPos.getX(column), ColumnPos.getZ(column));
            if (result != expected) {
                throw new AssertionError("Preliminary surface mismatch at " + new ColumnPos(x, z)
                        + " (" + scope.stage() + "): expected " + expected + ", got " + result);
            }
            AquiferReuseProbe.verifiedHeight(scope);
        }
        return result;
    }

    @Unique
    private int auramSmoke$uncachedHeight(int x, int z) {
        int floor = noiseSettings.minY();
        for (int y = floor + noiseSettings.height(); y >= floor; y -= noiseSettings.getCellHeight()) {
            if (initialDensityNoJaggedness.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0.390625) {
                return y;
            }
        }
        return Integer.MAX_VALUE;
    }
}

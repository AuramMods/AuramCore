package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.AquiferReuseProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NoiseChunk.class)
public abstract class AquiferSurfaceProbeMixin {
    @Shadow @Final private Long2IntMap preliminarySurfaceLevel;
    @Shadow @Final private NoiseSettings noiseSettings;
    @Shadow @Final private Blender blender;

    @WrapMethod(method = "preliminarySurfaceLevel")
    private int auramSmoke$recordSurfaceScan(int x, int z, Operation<Integer> original) {
        if (!AquiferReuseProbe.ENABLED) return original.call(x, z);
        var scope = AquiferReuseProbe.current();
        if (scope == null) return original.call(x, z);
        long column = ChunkPos.asLong(QuartPos.toBlock(QuartPos.fromBlock(x)), QuartPos.toBlock(QuartPos.fromBlock(z)));
        long start = AquiferReuseProbe.beforeScan(scope, preliminarySurfaceLevel.containsKey(column));
        int result = original.call(x, z);
        AquiferReuseProbe.afterScan(scope, start, blender == Blender.empty(), noiseSettings, column, result);
        return result;
    }
}

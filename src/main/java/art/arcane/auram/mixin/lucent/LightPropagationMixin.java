package art.arcane.auram.mixin.lucent;

import com.legacy.lucent.core.LucentConfig;
import com.legacy.lucent.core.dynamic_lighting.LightData;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Map;

@Pseudo
@Mixin(targets = "com.legacy.lucent.core.dynamic_lighting.DynamicLightingEngine", remap = false)
public abstract class LightPropagationMixin {
    @Unique
    private static final Direction[] auram$DIRECTIONS = Direction.values();

    @Shadow
    private static boolean addLightSource(Map<BlockPos, LightData> lightData, BlockPos pos, int level,
                                          LightData.SourcePos source) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean canLightPass(BlockPos from, BlockPos to, Direction direction) {
        throw new AssertionError();
    }

    @Inject(method = "addLights", at = @At("HEAD"), cancellable = true)
    private static void auram$propagateByLevel(Map<BlockPos, LightData> lightData, BlockPos origin, int initialLevel,
                                               LightData.SourcePos source, CallbackInfo ci) {
        // Position keys below are offsets from the source. BlockPos packs
        // 12 signed Y bits, so retain Lucent's path for larger custom radii.
        if (initialLevel < 0 || initialLevel > 2047) return;
        ci.cancel();
        if (!addLightSource(lightData, origin, initialLevel, source)) return;

        LongOpenHashSet visited = new LongOpenHashSet();
        visited.add(0);
        ArrayList<BlockPos> frontier = new ArrayList<>();
        ArrayList<BlockPos> next = new ArrayList<>();
        frontier.add(origin);
        for (int level = initialLevel - 1; !frontier.isEmpty() && level > (LucentConfig.CLIENT.smoothBlending.get() ? -1 : 0); level--) {
            // Every path to a cell in this frontier offers the same level. A
            // blocked edge leaves the destination eligible through other edges.
            for (BlockPos from : frontier) {
                for (Direction direction : auram$DIRECTIONS) {
                    int x = from.getX() + direction.getStepX();
                    int y = from.getY() + direction.getStepY();
                    int z = from.getZ() + direction.getStepZ();
                    long key = BlockPos.asLong(x - origin.getX(), y - origin.getY(), z - origin.getZ());
                    if (visited.contains(key)) continue;
                    BlockPos to = new BlockPos(x, y, z);
                    if (canLightPass(from, to, direction) && addLightSource(lightData, to, level, source)) {
                        visited.add(key);
                        next.add(to);
                    }
                }
            }
            frontier.clear();
            var consumed = frontier;
            frontier = next;
            next = consumed;
        }
    }
}

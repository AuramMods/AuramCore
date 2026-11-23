package art.arcane.auram.mixin.immersiveweathering;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;

@Pseudo
@Mixin(targets = "com.ordana.immersive_weathering.data.block_growths.BlockGrowthHandler", remap = false)
abstract class BlockGrowthFastPathMixin {
    @Shadow @Final private static Map<Object, Set<?>> UNIVERSAL_GROWTHS;
    @Shadow @Final private static Map<Object, Map<Block, Set<?>>> GROWTH_FOR_BLOCK;

    @Inject(method = "tickBlock", at = @At(value = "INVOKE", target =
            "Lcom/google/common/base/Suppliers;memoize(Lcom/google/common/base/Supplier;)Lcom/google/common/base/Supplier;"),
            cancellable = true)
    private static void auram$skipEmptyGrowths(@Coerce Object source, BlockState state,
                                               ServerLevel level, BlockPos pos, CallbackInfo ci) {
        // Let the mod run its enable and conditional-block checks first. The
        // original path owns the order, biome lookup and RNG whenever a rule applies.
        Set<?> universal = UNIVERSAL_GROWTHS.get(source);
        if (universal != null && !universal.isEmpty()) return;
        Map<Block, Set<?>> byBlock = GROWTH_FOR_BLOCK.get(source);
        Set<?> rules = byBlock == null ? null : byBlock.get(state.getBlock());
        if (rules == null || rules.isEmpty()) ci.cancel();
    }
}

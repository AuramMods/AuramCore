package art.arcane.auram.mixin.felling;

import art.arcane.auram.compat.adapt.FelledTreeXp;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "me.adda.enhanced_falling_trees.entity.TreeEntity", remap = false)
public abstract class TreeRemovalMixin {
    @WrapOperation(method = "destroyTree", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;I)Z"), require = 1)
    private static boolean auram$creditRemovedBlock(Level level, BlockPos pos, BlockState replacement, int flags,
                                                     Operation<Boolean> remove, @Local(argsOnly = true) Player player) {
        FelledTreeXp.Break felled = FelledTreeXp.snapshot(level, pos, player);
        boolean removed = remove.call(level, pos, replacement, flags);
        if (removed && felled != null && level.getBlockState(pos).isAir()) felled.credit();
        return removed;
    }
}

package art.arcane.auram.mixin.felling;

import art.arcane.auram.compat.adapt.FelledTreeXp;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.function.Predicate;

@Pseudo
@Mixin(targets = "me.adda.enhanced_falling_trees.event.EventHandler", remap = false)
public abstract class PlayerTreeCutMixin {
    @WrapOperation(method = "makeTreeFall(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/LevelAccessor;"
            + "Lnet/minecraft/world/entity/player/Player;)Z", at = @At(value = "INVOKE", target =
            "Ljava/util/Optional;filter(Ljava/util/function/Predicate;)Ljava/util/Optional;"), require = 1)
    private static Optional<?> auram$captureCuttingTool(Optional<?> tree, Predicate<Object> fell,
                                                        Operation<Optional<?>> filter,
                                                        @Local(argsOnly = true) BlockPos origin,
                                                        @Local(argsOnly = true) LevelAccessor level,
                                                        @Local(argsOnly = true) Player player) {
        return filter.call(tree, (Predicate<Object>) type ->
                FelledTreeXp.duringPlayerCut(origin, level, player, () -> fell.test(type)));
    }
}

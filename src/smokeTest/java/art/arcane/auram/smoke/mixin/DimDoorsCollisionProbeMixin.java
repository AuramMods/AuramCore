package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.DimDoorsCollisionSmoke;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.dimdev.dimdoors.block.door.DimensionalDoorBlock", remap = false)
public abstract class DimDoorsCollisionProbeMixin {
    @Inject(method = "onAfterMovePlayerCollision", at = @At("HEAD"), cancellable = true)
    private void auramSmoke$observeCollision(BlockState state, ServerLevel level, BlockPos position,
                                              ServerPlayer player, Vec3 movement,
                                              CallbackInfoReturnable<InteractionResult> ci) {
        InteractionResult result = DimDoorsCollisionSmoke.observe(player, position, movement);
        if (result != null) ci.setReturnValue(result);
    }
}

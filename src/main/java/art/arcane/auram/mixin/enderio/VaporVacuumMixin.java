package art.arcane.auram.mixin.enderio;

import art.arcane.auram.vapor.EnderIoVapor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.enderio.enderio.content.machines.vacuum.VacuumMachineBlockEntity", remap = false)
abstract class VaporVacuumMixin {
    @Unique private long auram$lastEmission = Long.MIN_VALUE;

    @Inject(method = "attractEntities", at = @At(value = "INVOKE",
            target = "Lcom/enderio/enderio/foundation/util/AttractionUtil;moveToPos(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;DDD)Z"))
    private void auram$emitWhilePulling(Level world, BlockPos pos, int range, CallbackInfo callback) {
        if (world instanceof ServerLevel level && auram$lastEmission != level.getGameTime()) {
            auram$lastEmission = level.getGameTime();
            EnderIoVapor.operating((BlockEntity) (Object) this);
        }
    }
}

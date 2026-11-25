package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.vapor.client.VaporClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
abstract class ClientMachineIndexMixin {
    @Inject(method = "setBlockEntity", at = @At("RETURN"))
    private void auram$indexMachine(BlockEntity entity, CallbackInfo callback) {
        var chunk = (LevelChunk) (Object) this;
        if (chunk.getLevel() instanceof ClientLevel level) VaporClient.machineAdded(level, entity);
    }

    @Inject(method = "removeBlockEntity", at = @At("RETURN"))
    private void auram$forgetMachine(BlockPos pos, CallbackInfo callback) {
        var chunk = (LevelChunk) (Object) this;
        if (chunk.getLevel() instanceof ClientLevel level) VaporClient.machineRemoved(level, pos);
    }
}

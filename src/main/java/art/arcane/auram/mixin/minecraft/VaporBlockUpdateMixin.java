package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.vapor.VaporDemonstrators;
import art.arcane.auram.vapor.client.VaporClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class VaporBlockUpdateMixin {
    @Inject(method = "sendBlockUpdated", at = @At("TAIL"))
    private void auram$vaporBlockChanged(BlockPos pos, BlockState before, BlockState after, int flags, CallbackInfo ci) {
        if (before == after) return;
        var level = (ClientLevel) (Object) this;
        VaporDemonstrators.blockChanged(level, pos, before, after);
        VaporClient.blockChanged(level, pos);
    }
}

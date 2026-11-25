package art.arcane.auram.mixin.tconstruct;

import art.arcane.auram.vapor.TinkersVapor;
import art.arcane.auram.vapor.VaporDemonstrators;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.smeltery.block.entity.ChannelBlockEntity;

@Mixin(value = ChannelBlockEntity.class, remap = false)
abstract class VaporChannelMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void auram$vapor(BlockState state, CallbackInfo ci) {
        var channel = (ChannelBlockEntity) (Object) this;
        if (channel.getLevel() instanceof ServerLevel level && VaporDemonstrators.legacyViewers(level) && !channel.getFluid().isEmpty()) {
            TinkersVapor.channel(level, channel.getBlockPos());
        }
    }
}

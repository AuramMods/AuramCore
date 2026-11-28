package art.arcane.auram.mixin.felling;

import art.arcane.adapt.mod.platform.world.ModBlockRef;
import art.arcane.auram.compat.adapt.FelledTreeXp;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModBlockRef.class, remap = false)
public abstract class AdaptBlockSnapshotMixin implements FelledTreeXp.BlockSnapshot {
    @Unique
    private BlockState auram$felledState;

    @Override
    public void auram$rememberFelledState(BlockState state) {
        auram$felledState = state;
    }

    @Inject(method = "blockState", at = @At("HEAD"), cancellable = true, require = 1)
    private void auram$useFelledState(CallbackInfoReturnable<BlockState> callback) {
        if (auram$felledState != null) callback.setReturnValue(auram$felledState);
    }
}

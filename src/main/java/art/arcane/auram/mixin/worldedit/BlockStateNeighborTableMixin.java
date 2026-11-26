package art.arcane.auram.mixin.worldedit;

import art.arcane.auram.compat.worldedit.DeferredNeighborTable;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Pseudo
@Mixin(targets = "com.sk89q.worldedit.world.block.BlockState", remap = false)
abstract class BlockStateNeighborTableMixin implements DeferredNeighborTable {
    @Unique
    private volatile Map<?, ?> auram$pendingStates;

    @Shadow
    private void populate(Map<?, ?> states) {
        throw new AssertionError();
    }

    @WrapOperation(method = "generateStateMap", at = @At(value = "INVOKE", target =
            "Lcom/sk89q/worldedit/world/block/BlockState;populate(Ljava/util/Map;)V"))
    private static void auram$registerNeighborTable(@Coerce Object state, Map<?, ?> states, Operation<Void> original) {
        ((DeferredNeighborTable) state).auram$deferNeighborTable(states);
    }

    @Override
    public void auram$deferNeighborTable(Map<?, ?> states) {
        auram$pendingStates = states;
    }

    @Inject(method = "with(Lcom/sk89q/worldedit/registry/state/Property;Ljava/lang/Object;)"
            + "Lcom/sk89q/worldedit/world/block/BlockState;", at = @At("HEAD"))
    private void auram$buildNeighborTableOnUse(CallbackInfoReturnable<?> callback) {
        if (auram$pendingStates == null) return;
        synchronized (this) {
            Map<?, ?> states = auram$pendingStates;
            if (states != null) {
                populate(states);
                auram$pendingStates = null;
            }
        }
    }
}

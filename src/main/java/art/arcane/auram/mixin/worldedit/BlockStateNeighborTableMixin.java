package art.arcane.auram.mixin.worldedit;

import art.arcane.auram.compat.worldedit.BlockStateIndex;
import art.arcane.auram.compat.worldedit.BlockStateIndexHolder;
import com.google.common.collect.Table;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.Map;

@Pseudo
@Mixin(targets = "com.sk89q.worldedit.world.block.BlockState", remap = false)
abstract class BlockStateNeighborTableMixin implements BlockStateIndexHolder {
    @Unique
    private BlockStateIndex auram$stateIndex;

    @Unique
    private volatile int auram$stateOrdinalPlusOne;

    @Shadow @Final
    private Map<?, ?> values;

    @WrapOperation(method = "generateStateMap", at = @At(value = "INVOKE", target =
            "Lcom/sk89q/worldedit/world/block/BlockState;populate(Ljava/util/Map;)V"))
    private static void auram$registerStateIndex(@Coerce Object state, Map<? extends Map<?, ?>, ?> states,
                                                Operation<Void> original,
                                                @Share("stateIndex") LocalRef<BlockStateIndex> sharedIndex) {
        var index = sharedIndex.get();
        if (index == null) {
            index = new BlockStateIndex(states);
            sharedIndex.set(index);
        }
        ((BlockStateIndexHolder) state).auram$setBlockStateIndex(index);
    }

    @Override
    public void auram$setBlockStateIndex(BlockStateIndex index) {
        this.auram$stateIndex = index;
    }

    @WrapOperation(method = "with(Lcom/sk89q/worldedit/registry/state/Property;Ljava/lang/Object;)"
            + "Lcom/sk89q/worldedit/world/block/BlockState;", at = @At(value = "INVOKE", target =
            "Lcom/google/common/collect/Table;get(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object auram$findNeighborState(Table<?, ?, ?> table, Object property, Object value,
                                           Operation<Object> original) {
        var index = this.auram$stateIndex;
        if (index == null) return original.call(table, property, value);
        int ordinal = this.auram$stateOrdinalPlusOne - 1;
        if (ordinal < 0) {
            ordinal = index.ordinal(this.values);
            this.auram$stateOrdinalPlusOne = ordinal + 1;
        }
        return index.neighbor(ordinal, property, value);
    }
}

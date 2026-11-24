package art.arcane.auram.mixin.titanium;

import art.arcane.auram.vapor.FurnaceFuelUse;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.hrznstudio.titanium.block.tile.GeneratorTile", remap = false)
abstract class GeneratorVaporMixin {
    @WrapOperation(method = "lambda$new$1", at = @At(value = "INVOKE",
            target = "Lcom/hrznstudio/titanium/block/tile/GeneratorTile;consumeFuel()I"))
    private int auram$observeConsumedFuel(@Coerce Object generator, Operation<Integer> original) {
        return FurnaceFuelUse.consume((BlockEntity) generator, () -> original.call(generator));
    }

    @Inject(method = "lambda$new$3", at = @At("TAIL"))
    private void auram$burningFurnaceFuel(CallbackInfo callback) {
        FurnaceFuelUse.burnTick((BlockEntity) (Object) this);
    }
}

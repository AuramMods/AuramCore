package art.arcane.auram.mixin.enderio;

import art.arcane.auram.vapor.EnderIoVapor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {
        "com.enderio.enderio.content.machines.sag_mill.SagMillBlockEntity",
        "com.enderio.enderio.content.machines.alloy.AlloySmelterBlockEntity",
        "com.enderio.enderio.content.machines.alloy.PrimitiveAlloySmelterBlockEntity",
        "com.enderio.enderio.content.machines.slicer.SlicerBlockEntity",
        "com.enderio.enderio.content.machines.painting.PaintingMachineBlockEntity",
        "com.enderio.enderio.content.machines.stirling_generator.StirlingGeneratorBlockEntity",
        "com.enderio.enderio.content.machines.soul_engine.SoulEngineBlockEntity",
        "com.enderio.enderio.content.machines.farming_station.FarmingStationBlockEntity"
}, remap = false)
abstract class VaporProcessingMachineMixin {
    @Shadow public abstract boolean isActive();

    @Inject(method = "serverTick()V", at = @At("TAIL"))
    private void auram$emitWhileProcessing(CallbackInfo callback) {
        if (isActive()) EnderIoVapor.operating((BlockEntity) (Object) this);
    }
}

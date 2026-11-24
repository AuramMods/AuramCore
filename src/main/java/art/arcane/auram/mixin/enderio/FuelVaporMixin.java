package art.arcane.auram.mixin.enderio;

import art.arcane.auram.vapor.MachineVapor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {
        "com.enderio.enderio.content.machines.alloy.PrimitiveAlloySmelterBlockEntity",
        "com.enderio.enderio.content.machines.stirling_generator.StirlingGeneratorBlockEntity"
}, remap = false)
abstract class FuelVaporMixin {
    @Shadow(remap = false) private int burnTime;

    @Inject(method = "serverTick()V", at = @At("HEAD"))
    private void auram$burnTick(CallbackInfo callback) {
        if (burnTime > 0) MachineVapor.burning((BlockEntity) (Object) this);
    }
}

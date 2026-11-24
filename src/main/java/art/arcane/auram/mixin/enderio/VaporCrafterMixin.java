package art.arcane.auram.mixin.enderio;

import art.arcane.auram.vapor.EnderIoVapor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.enderio.enderio.content.storage.crafter.CrafterBlockEntity", remap = false)
abstract class VaporCrafterMixin {
    @Inject(method = "tryCraft()V", at = @At("TAIL"))
    private void auram$emitAfterCraft(CallbackInfo callback) {
        EnderIoVapor.operated((BlockEntity) (Object) this);
    }
}

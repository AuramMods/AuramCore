package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.client.ClientBranding;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class ApplicationTitleMixin {
    @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
    private void auram$title(CallbackInfoReturnable<String> callback) {
        callback.setReturnValue(ClientBranding.name());
    }
}

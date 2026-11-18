package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.client.ClientBranding;
import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.server.packs.PackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

@Mixin(Window.class)
public abstract class ApplicationIconMixin {
    @Shadow @Final private long window;

    @Inject(method = "setIcon", at = @At("HEAD"), cancellable = true)
    private void auram$icon(PackResources resources, IconSet icons, CallbackInfo callback) throws IOException {
        ClientBranding.setIcon(window);
        callback.cancel();
    }
}

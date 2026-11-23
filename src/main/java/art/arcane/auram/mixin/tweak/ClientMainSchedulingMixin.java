package art.arcane.auram.mixin.tweak;

import art.arcane.auram.tweak.ThreadScheduling;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public abstract class ClientMainSchedulingMixin {
    @Inject(method = "main", at = @At("HEAD"), remap = false)
    private static void auram$setGamePriority(String[] args, CallbackInfo callback) {
        ThreadScheduling.SETTINGS.configureGameThread(Thread.currentThread());
    }
}

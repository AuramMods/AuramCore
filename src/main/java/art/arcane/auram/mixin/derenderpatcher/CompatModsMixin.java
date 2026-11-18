package art.arcane.auram.mixin.derenderpatcher;

import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Use Forge's indexed lookup on the render path. */
@Pseudo
@Mixin(targets = "com.derenderpatcher.compat.CompatMods", remap = false)
public abstract class CompatModsMixin {
    @Inject(method = "isModLoaded(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void auram$lookupLoadedMod(String id, CallbackInfoReturnable<Boolean> cir) {
        try {
            ModList mods = ModList.get();
            if (mods != null) {
                cir.setReturnValue(mods.isLoaded(id));
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Let CompatMods handle an unavailable Forge lookup as it does at startup.
        }
    }
}

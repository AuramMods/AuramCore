package art.arcane.auram.mixin.soundphysics;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.sonicether.soundphysics.config.SoundRateConfig", remap = false)
abstract class UnknownSoundRateLogMixin {
    @WrapWithCondition(method = "logIfUnknownSound", at = @At(value = "INVOKE",
            target = "Lcom/sonicether/soundphysics/Loggers;log(Ljava/lang/String;[Ljava/lang/Object;)V"))
    private boolean auram$skipMissingSoundNotice(String message, Object[] arguments) {
        // Packs can retain rates for sounds from optional mods. Keep those rates and the parse warnings.
        return false;
    }
}

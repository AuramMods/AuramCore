package art.arcane.auram.mixin.tweak;

import art.arcane.auram.client.AnvilSounds;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class AnvilSoundPlaybackMixin {
    @WrapOperation(method = "levelEvent", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
    private void auram$replaceEnchantingSound(ClientLevel level, BlockPos pos, SoundEvent sound,
                                             SoundSource source, float volume, float pitch, boolean delay,
                                             Operation<Void> play) {
        play.call(level, pos, AnvilSounds.replacement(level, pos, sound), source, volume, pitch, delay);
    }
}

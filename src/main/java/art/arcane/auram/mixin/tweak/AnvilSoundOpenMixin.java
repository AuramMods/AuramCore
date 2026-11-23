package art.arcane.auram.mixin.tweak;

import art.arcane.auram.client.AnvilSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class AnvilSoundOpenMixin {
    @Inject(method = "handleOpenScreen", at = @At("RETURN"))
    private void auram$bindOpenedMenu(ClientboundOpenScreenPacket packet, CallbackInfo callback) {
        AnvilSounds.openedMenu(Minecraft.getInstance());
    }
}

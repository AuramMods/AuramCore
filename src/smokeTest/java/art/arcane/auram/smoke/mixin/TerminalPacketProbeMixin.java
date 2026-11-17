package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.TerminalPacketProbe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.tom.storagemod.network.NetworkHandler",remap=false)
public abstract class TerminalPacketProbeMixin {
    @Inject(method="sendTo",at=@At("HEAD"))
    private static void auramSmoke$packet(ServerPlayer player,CompoundTag packet,CallbackInfo ci) {
        TerminalPacketProbe.record(packet);
    }
}

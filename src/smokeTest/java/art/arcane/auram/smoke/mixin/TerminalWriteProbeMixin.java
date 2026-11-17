package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.TerminalPacketProbe;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.tom.storagemod.util.TerminalSyncManager",remap=false)
public abstract class TerminalWriteProbeMixin {
    @Inject(method="writeMiniStack",at=@At("HEAD"))
    private void auramSmoke$overflow(FriendlyByteBuf buffer,@Coerce Object stack,CallbackInfo ci) {
        TerminalPacketProbe.overflowWrites++;
    }
}

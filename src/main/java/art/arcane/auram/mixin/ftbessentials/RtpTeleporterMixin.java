package art.arcane.auram.mixin.ftbessentials;

import art.arcane.auram.util.FtbRtpSearch;
import net.minecraft.server.level.ServerPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbessentials.util.WarmupCooldownTeleporter", remap = false)
public abstract class RtpTeleporterMixin {
    @Shadow private long cooldown;

    @Inject(method = "teleportNow", at = @At("HEAD"), cancellable = true)
    private void auram$rejectDuplicateSearch(ServerPlayer player, Function<?, ?> finder, CallbackInfoReturnable<Object> ci) {
        if (FtbRtpSearch.isRtp(finder) && FtbRtpSearch.isActive(player)) {
            ci.setReturnValue(FtbRtpSearch.duplicateResult());
        }
    }

    @Inject(method = "teleportNow", at = @At(value = "INVOKE", target =
            "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;"), cancellable = true)
    private void auram$beginAfterNativeWarmup(ServerPlayer player, Function<?, ?> finder, CallbackInfoReturnable<Object> ci) {
        if (FtbRtpSearch.isRtp(finder) && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
            ci.setReturnValue(FtbRtpSearch.begin(this, player));
        }
    }

    @Redirect(method = "teleportNow", at = @At(value = "FIELD",
            target = "Ldev/ftb/mods/ftbessentials/util/WarmupCooldownTeleporter;cooldown:J", opcode = Opcodes.PUTFIELD))
    private void auram$retainOriginalCooldown(@Coerce Object teleporter, long deadline,
                                             ServerPlayer player, Function<?, ?> finder) {
        // FTB still owns the cooldown set at the start of the search.
        if (!(finder instanceof FtbRtpSearch.ReadyPosition)) cooldown = deadline;
    }

    @Inject(method = "cancelWarmup", at = @At("RETURN"))
    private static void auram$cancelSearch(ServerPlayer player, CallbackInfo ci) {
        FtbRtpSearch.interrupt(player);
    }
}

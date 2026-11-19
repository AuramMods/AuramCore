package art.arcane.auram.mixin.ftbessentials;

import art.arcane.auram.util.FtbRtpSearch;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbessentials.command.TeleportCommands", remap = false)
public abstract class RtpCommandMixin {
    @ModifyArg(method = "lambda$rtp$12", at = @At(value = "INVOKE", target =
            "Ldev/ftb/mods/ftbessentials/util/WarmupCooldownTeleporter;teleport(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Function;)Ldev/ftb/mods/ftbessentials/util/TeleportPos$TeleportResult;"),
            index = 1)
    private static Function<ServerPlayer, ?> auram$markRandomTeleport(Function<ServerPlayer, ?> finder) {
        return FtbRtpSearch.markRtp(finder);
    }
}

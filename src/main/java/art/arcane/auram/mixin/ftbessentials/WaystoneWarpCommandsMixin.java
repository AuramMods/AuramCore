package art.arcane.auram.mixin.ftbessentials;

import art.arcane.auram.waystone.DestinationFeedback;
import art.arcane.auram.waystone.WaystoneDestinations;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ftb.mods.ftbessentials.command.WarpCommands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WarpCommands.class, remap = false)
abstract class WaystoneWarpCommandsMixin {
    @Inject(method = "setWarp", at = @At("HEAD"), cancellable = true)
    private static void auram$requireOperator(ServerPlayer player, String name, CallbackInfoReturnable<Integer> cir) {
        if (!player.hasPermissions(2)) {
            DestinationFeedback.warning(player, Component.translatable("auram.waystone.warp_operator_required"));
            cir.setReturnValue(0);
        } else if (WaystoneDestinations.rejectWarpCommand(player, name)) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "deleteWarp", at = @At("HEAD"), cancellable = true)
    private static void auram$protectWaystoneWarp(ServerPlayer player, String name, CallbackInfoReturnable<Integer> cir) {
        if (WaystoneDestinations.rejectWarpCommand(player, name)) cir.setReturnValue(0);
    }

    @WrapOperation(method = "setWarp", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/server/level/ServerPlayer;displayClientMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private static void auram$confirmManualWarp(ServerPlayer player, Component message, boolean overlay, Operation<Void> original,
                                               @Local(argsOnly = true) String name) {
        DestinationFeedback.saved(player, name, true);
    }
}

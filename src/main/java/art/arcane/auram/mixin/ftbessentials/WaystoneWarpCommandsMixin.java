package art.arcane.auram.mixin.ftbessentials;

import art.arcane.auram.waystone.WaystoneDestinations;
import dev.ftb.mods.ftbessentials.command.WarpCommands;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WarpCommands.class, remap = false)
abstract class WaystoneWarpCommandsMixin {
    @Inject(method = {"setWarp", "deleteWarp"}, at = @At("HEAD"), cancellable = true)
    private static void auram$protectWaystoneWarp(ServerPlayer player, String name, CallbackInfoReturnable<Integer> cir) {
        if (WaystoneDestinations.rejectWarpCommand(player, name)) cir.setReturnValue(0);
    }
}

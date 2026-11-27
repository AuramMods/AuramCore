package art.arcane.auram.mixin.ftbessentials;

import art.arcane.auram.waystone.WaystoneDestinations;
import dev.ftb.mods.ftbessentials.command.HomeCommands;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = HomeCommands.class, remap = false)
abstract class WaystoneHomeCommandsMixin {
    @Inject(method = {"setHome", "delHome"}, at = @At("HEAD"), cancellable = true)
    private static void auram$protectWaystoneHome(ServerPlayer player, String name, CallbackInfoReturnable<Integer> cir) {
        if (WaystoneDestinations.rejectHomeCommand(player, name)) cir.setReturnValue(0);
    }
}

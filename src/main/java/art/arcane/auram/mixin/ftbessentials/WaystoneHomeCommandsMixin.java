package art.arcane.auram.mixin.ftbessentials;

import art.arcane.auram.waystone.DestinationFeedback;
import art.arcane.auram.waystone.WaystoneDestinations;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ftb.mods.ftbessentials.command.HomeCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = HomeCommands.class, remap = false)
abstract class WaystoneHomeCommandsMixin {
    @Inject(method = "setHome", at = @At("HEAD"), cancellable = true)
    private static void auram$restrictManualHomes(ServerPlayer player, String name, CallbackInfoReturnable<Integer> cir) {
        if (!"home".equalsIgnoreCase(name)) {
            DestinationFeedback.warning(player, Component.translatable("auram.waystone.use_waystones",
                    Component.translatable("auram.waystone.waystones").withStyle(ChatFormatting.GOLD)));
            cir.setReturnValue(0);
        } else if (WaystoneDestinations.rejectHomeCommand(player, name)) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "delHome", at = @At("HEAD"), cancellable = true)
    private static void auram$protectWaystoneHome(ServerPlayer player, String name, CallbackInfoReturnable<Integer> cir) {
        if (WaystoneDestinations.rejectHomeCommand(player, name)) cir.setReturnValue(0);
    }

    @WrapOperation(method = "lambda$setHome$13", at = @At(value = "INVOKE", remap = true, ordinal = 0,
            target = "Lnet/minecraft/server/level/ServerPlayer;displayClientMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private static void auram$confirmDefaultHome(ServerPlayer player, Component message, boolean overlay, Operation<Void> original) {
        DestinationFeedback.saved(player, "home", false);
    }

    @ModifyArg(method = "lambda$setHome$13", at = @At(value = "INVOKE", remap = true, ordinal = 1,
            target = "Lnet/minecraft/server/level/ServerPlayer;displayClientMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private static Component auram$styleHomeLimit(Component message) {
        return message.copy().withStyle(ChatFormatting.YELLOW);
    }
}

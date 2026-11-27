package art.arcane.auram.mixin.waystones;

import art.arcane.auram.waystone.DestinationFeedback;
import art.arcane.auram.waystone.WaystoneDestinations;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.blay09.mods.waystones.core.Waystone;
import net.blay09.mods.waystones.network.message.EditWaystoneMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EditWaystoneMessage.class, remap = false)
abstract class EditWaystoneMixin {
    @Shadow @Final private boolean isGlobal;

    @Inject(method = "handle", at = @At(value = "INVOKE",
            target = "Lnet/blay09/mods/waystones/core/Waystone;setName(Ljava/lang/String;)V"), cancellable = true)
    private static void auram$validateDestinationNames(ServerPlayer player, EditWaystoneMessage message, CallbackInfo ci,
                                                       @Local Waystone stone, @Local String legalName) {
        if (!WaystoneDestinations.isPlayerWaystone(stone) || stone.getOwnerUid() == null) return;
        boolean global = ((EditWaystoneMixin) (Object) message).isGlobal;
        Component error = stone.isGlobal() != global && !WaystoneDestinations.isOwnedBy(stone, player)
                && !PlayerWaystoneManager.mayEditGlobalWaystones(player)
                ? Component.translatable("auram.waystone.global_permission")
                : WaystoneDestinations.editError(player.getServer(), stone, legalName, global);
        if (error != null) {
            DestinationFeedback.warning(player, error);
            ci.cancel();
        }
    }

    @WrapOperation(method = "handle", at = @At(value = "INVOKE",
            target = "Lnet/blay09/mods/waystones/core/PlayerWaystoneManager;mayEditGlobalWaystones(Lnet/minecraft/world/entity/player/Player;)Z"))
    private static boolean auram$allowOwnerGlobalToggle(Player player, Operation<Boolean> original, @Local Waystone stone) {
        return WaystoneDestinations.isOwnedBy(stone, player) || original.call(player);
    }

    @Inject(method = "handle", at = @At(value = "INVOKE",
            target = "Lnet/blay09/mods/waystones/core/WaystoneSyncManager;sendWaystoneUpdateToAll(Lnet/minecraft/server/MinecraftServer;Lnet/blay09/mods/waystones/api/IWaystone;)V"))
    private static void auram$refreshDestinations(ServerPlayer player, EditWaystoneMessage message, CallbackInfo ci,
                                                 @Local Waystone stone) {
        WaystoneDestinations.changed();
        if (WaystoneDestinations.isPlayerWaystone(stone) && stone.getOwnerUid() != null) {
            DestinationFeedback.saved(player, stone.getName(), stone.isGlobal());
        }
    }
}
